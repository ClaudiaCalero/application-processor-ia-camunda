package com.claudia.tramitador.service;

import com.claudia.tramitador.entity.Request;
import com.claudia.tramitador.enums.RequestCategory;
import com.claudia.tramitador.enums.RequestStatus;
import com.claudia.tramitador.model.Classification;
import com.claudia.tramitador.repository.RequestRepository;
import io.camunda.client.api.response.ActivatedJob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RequestWorkersTest {
    private static final double THRESHOLD = 0.8;
    private static final long REQUEST_ID = 1L;

    @Mock
    private RequestRepository repository;

    @Mock
    private AIClassifier aiClassifier;

    @Mock
    private ActivatedJob job;

    @InjectMocks
    private RequestWorkers requestWorkers;

    private Request request;

    @BeforeEach
    void setUp() {
        request = new Request("I received an invoice for 250 euros");
        request.setId(REQUEST_ID);

        // Mockito does not inject values field, we set the threshold manually
        ReflectionTestUtils.setField(requestWorkers, "threshold", THRESHOLD);
    }

    private void givenStoredRequestAndJob(Map<String, Object> extraVariables) {
        Map<String, Object> variables = new HashMap<>(extraVariables);
        variables.put("requestId", REQUEST_ID);

        when(job.getVariablesAsMap()).thenReturn(variables);
        when(repository.findById(REQUEST_ID)).thenReturn(Optional.of(request));
    }

    private void givenModelReturn(Classification classification) {
       when(aiClassifier.classify(request.getText())).thenReturn(classification);
    }

    @Test
    void classifyRequest_shouldStoreResultAndSkpReview_whenScoreIsHigh() {
        givenStoredRequestAndJob(Map.of());
        givenModelReturn(new Classification(RequestCategory.INVOICE,250.0, "Invoice received", 0.95));

        Map<String, Object> result = requestWorkers.classifyRequest(job);

        assertThat(request.getCategory()).isEqualTo("INVOICE");
        assertThat(request.getScore()).isEqualTo(0.95);
        assertThat(request.getSummary()).isEqualTo("Invoice received");
        assertThat(request.getAmount()).isEqualTo(250.0);
        assertThat(request.getStatus()).isEqualTo(RequestStatus.RECEIVED);

        assertThat(result)
                .containsEntry("reviewRequired", false)
                .containsEntry("category", "INVOICE")
                .containsEntry("score", 0.95);
        verify(repository).save(request);
    }

    @Test
    void classifyRequest_shouldRequireReview_whenScoreIsLow() {
        givenStoredRequestAndJob(Map.of());
        givenModelReturn(new Classification(RequestCategory.COMPLAINT, null, "Customer complaint", 0.50));

        Map<String, Object> result = requestWorkers.classifyRequest(job);

        assertThat(request.getStatus()).isEqualTo(RequestStatus.IN_REVIEW);
        assertThat(result).containsEntry("reviewRequired", true);

        verify(repository).save(request);
    }

    @Test
    void classifyRequest_shouldRequireReview_whenCategoryIsOtherEvenWithHighScore() {
        givenStoredRequestAndJob(Map.of());
        givenModelReturn(new Classification(RequestCategory.OTHER, null, "Unclassified request" , 0.99));

        Map<String, Object> result = requestWorkers.classifyRequest(job);

        assertThat(request.getStatus()).isEqualTo(RequestStatus.IN_REVIEW);
        assertThat(result).containsEntry("reviewRequired", true);

    }

    @Test
    void classifyRequest_shouldNotRequiredReview_whenScoreEqualsThreshold() {
        givenStoredRequestAndJob(Map.of());
        givenModelReturn(new Classification(RequestCategory.INVOICE, null, "Incident", THRESHOLD));

        Map<String, Object> result = requestWorkers.classifyRequest(job);

        assertThat(result).containsEntry("reviewRequired", false);
        assertThat(request.getStatus()).isEqualTo(RequestStatus.RECEIVED);
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0, -0.5})
    void classifyRequest_shouldDiscardNonPositiveAMounts(double amount) {
        givenStoredRequestAndJob(Map.of());
        givenModelReturn(new Classification(RequestCategory.INVOICE, amount, "Invoice without valid amount", 0.95));

        requestWorkers.classifyRequest(job);

        assertThat(request.getAmount()).isNull();
        verify(repository).save(request);
    }


    @Test
    void classifyRequest_shouldKeepSummaryUntouched_whenItFitsTheColumn() {
        givenStoredRequestAndJob(Map.of());
        givenModelReturn(new Classification(RequestCategory.INVOICE, 100.0, "s".repeat(2000), 0.95));

        requestWorkers.classifyRequest(job);

        assertThat(request.getSummary()).hasSize(2000);
    }

    @Test
    void classifyRequest_shouldTruncateSummaryToColumnLength() {
        givenStoredRequestAndJob(Map.of());
        givenModelReturn(new Classification(RequestCategory.INVOICE, 100.0, "s".repeat(2500), 0.95));

        requestWorkers.classifyRequest(job);

        assertThat(request.getSummary()).hasSize(2000);
    }


    @Test
    void classifyRequest_shouldLeaveRequestUntouched_whenModelFails() {
        givenStoredRequestAndJob(Map.of());
        when(aiClassifier.classify(request.getText()))
                .thenThrow(new IllegalStateException("Ollama unreachable"));

        assertThatThrownBy(() -> requestWorkers.classifyRequest(job))
                .isInstanceOf(IllegalStateException.class);

        assertThat(request.getCategory()).isNull();
        assertThat(request.getScore()).isNull();
        assertThat(request.getStatus()).isEqualTo(RequestStatus.RECEIVED);
        verify(repository, never()).save(any());
    }

    @Test
    void classifyRequest_shouldFailBeforeCallingTheModel_WhenRequestDoesNotExist() {
        when(job.getVariablesAsMap()).thenReturn(Map.of("requestId", 99L));
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> requestWorkers.classifyRequest(job))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Request not found: 99");

        verifyNoInteractions(aiClassifier);
        verify(repository, never()).save(any());
    }


    @Test
    void finalizeRequest_shouldComplete_withoutHumanReview_whenReviewWasNotRequired() {
        givenStoredRequestAndJob(Map.of("category", "INVOICE", "reviewRequired", false));

        requestWorkers.finalizeRequest(job);

        assertThat(request.getStatus()).isEqualTo(RequestStatus.COMPLETED);
        assertThat(request.getCategory()).isEqualTo("INVOICE");
        assertThat(request.isReviewedByPerson()).isFalse();

        verify(repository).save(request);
    }


    @Test
    void finalizeRequest_shouldRecordHumanReview_whenRequestWentThroughReview() {
        request.setStatus(RequestStatus.IN_REVIEW);
        givenStoredRequestAndJob(Map.of("category", "COMPLAINT", "reviewRequired", true));

        requestWorkers.finalizeRequest(job);

        assertThat(request.getStatus()).isEqualTo(RequestStatus.COMPLETED);
        assertThat(request.getCategory()).isEqualTo("COMPLAINT");
        assertThat(request.isReviewedByPerson()).isTrue();

        verify(repository).save(request);
        }

    @Test
    void finalizeRequest_shouldKeepStoredCategory_whenProcessHasNoCategoryVariable() {
        request.setCategory("INVOICE");
        givenStoredRequestAndJob(Map.of("reviewRequired", false));

        requestWorkers.finalizeRequest(job);

        assertThat(request.getCategory()).isEqualTo("INVOICE");
        assertThat(request.getStatus()).isEqualTo(RequestStatus.COMPLETED);
    }

    @Test
    void finalizeRequest_shouldGiveTheSameResult_whenCamundaRetriesTheJob() {
        givenStoredRequestAndJob(Map.of("category", "COMPLAINT", "reviewRequired", true));

        requestWorkers.finalizeRequest(job);
        requestWorkers.finalizeRequest(job);

        assertThat(request.getStatus()).isEqualTo(RequestStatus.COMPLETED);
        assertThat(request.getCategory()).isEqualTo("COMPLAINT");
        assertThat(request.isReviewedByPerson()).isTrue();

    }


    @Test
    void finalizeRequest_shouldThrow_whenRequestDoesNotExist() {
        when(job.getVariablesAsMap())
                .thenReturn(Map.of("requestId", 99L));

        when(repository.findById(99L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> requestWorkers.finalizeRequest(job))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Request not found: 99");

        verify(repository, never()).save(any());
    }
}
