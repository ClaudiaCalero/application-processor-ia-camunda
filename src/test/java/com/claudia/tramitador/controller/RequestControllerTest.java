
package com.claudia.tramitador.controller;

import com.claudia.tramitador.entity.Request;
import com.claudia.tramitador.enums.RequestStatus;
import com.claudia.tramitador.repository.RequestRepository;
import io.camunda.client.CamundaClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Answers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;

import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static com.claudia.tramitador.enums.RequestStatus.RECEIVED;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RequestController.class)
class RequestControllerTest {

    private static final String VALID_BODY = "{\"text\":\"I received an invoice for 150 euros\"}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RequestRepository repository;

    @MockitoBean(answers = Answers.RETURNS_DEEP_STUBS)
    private CamundaClient camunda;

    private void repositoryAssignsId(long id) {
        when(repository.save(any(Request.class))).thenAnswer(invocation -> {
            Request saved = invocation.getArgument(0);
            saved.setId(id);
            return saved;
        });
    }


    @Test
    void createRequest_shouldSaveAndStartTheProcessDeployedFromTheBpmnFile() throws Exception {
        repositoryAssignsId(1L);

        mockMvc.perform(post("/api/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("RECEIVED"))
                .andExpect(jsonPath("$.text").value("I received an invoice for 150 euros"));

        verify(repository).save(any(Request.class));
        // the id must match the process id in tramitar-solicitud.bpmn
        verify(camunda.newCreateInstanceCommand()).bpmnProcessId("process-application");
    }

    @Test
    void createRequest_shouldAcceptTextLongerThan5000Characters() throws Exception {
        repositoryAssignsId(1L);

        mockMvc.perform(post("/api/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"" + "a".repeat(5000) + "\"}"))
                .andExpect(status().isCreated());

    }

    @Test
    void createRequest_shouldRejectTextWith5000Characters() throws Exception {
        mockMvc.perform(post("/api/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"" + "a".repeat(5001) + "\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(repository);
        verifyNoInteractions(camunda);
    }

    @Test
    void shouldListRequests() throws Exception {
        when(repository.findAll(any(org.springframework.data.domain.Sort.class)))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/requests"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));

        verify(repository).findAll(
                org.springframework.data.domain.Sort.by(
                        org.springframework.data.domain.Sort.Direction.DESC,
                        "createdAt"
                )
        );
    }

    @Test
    void shouldSaveRequestAndStartCamundaWorkflow() throws Exception {
        when(repository.save(any(Request.class)))
                .thenAnswer(invocation -> {
                    Request request = invocation.getArgument(0);
                    request.setId(1L);
                    return request;
                });

        mockMvc.perform(post("/api/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"text": "I want to apply for a Java developer position."}
                            """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.text")
                        .value("I want to apply for a Java developer position."));

        verify(repository).save(any(Request.class));
        verify(camunda).newCreateInstanceCommand();
    }

    @ParameterizedTest
    @ValueSource(strings = { "{\"text\":\"\"}",       // empty
            "{\"text\":\"   \"}",    // only spaces
            "{}",                    // field missing
            "{not json"              // malformed
    })
    void createRequest_shouldRejectInvalidBodiesWithoutTouchingDatabaseOrCamunda(String body) throws Exception {
        mockMvc.perform(post("/api/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(repository);
        verifyNoInteractions(camunda);
    }


    @Test
    void createRequest_shouldDeleteTheRowAndReturn503_whenCamundaIsDown() throws Exception {
        repositoryAssignsId(1L);
        when(camunda.newCreateInstanceCommand()).thenThrow(new RuntimeException("Camunda unavailable"));

        mockMvc.perform(post("/api/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isServiceUnavailable());

        verify(repository).save(any(Request.class));
        verify(repository).delete(any(Request.class));
    }

    @Test
    void listRequests_shouldQueryNewestFirst() throws Exception {
        when(repository.findAll(any(Sort.class))).thenReturn(List.of());

        mockMvc.perform(get("/api/requests"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        verify(repository).findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    @Test
    void listRequests_shouldExposeEveryFieldTheFrontendReads() throws Exception {
        Request request = new Request("I received an invoice for 150 euros");
        request.setId(7L);
        request.setCategory("INVOICE");
        request.setScore(0.95);
        request.setSummary("Invoice question");
        request.setAmount(150.0);
        request.setStatus(RequestStatus.IN_REVIEW);
        request.setReviewedByPerson(true);
        when(repository.findAll(any(Sort.class))).thenReturn(List.of(request));

        mockMvc.perform(get("/api/requests"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(7))
                .andExpect(jsonPath("$[0].text").value("I received an invoice for 150 euros"))
                .andExpect(jsonPath("$[0].category").value("INVOICE"))
                .andExpect(jsonPath("$[0].score").value(0.95))
                .andExpect(jsonPath("$[0].summary").value("Invoice question"))
                .andExpect(jsonPath("$[0].amount").value(150.0))
                .andExpect(jsonPath("$[0].status").value("IN_REVIEW"))
                .andExpect(jsonPath("$[0].reviewedByPerson").value(true))
                .andExpect(jsonPath("$[0].createdAt").exists());
    }
    @Test
    void cors_shouldAllowThePreflightFromTheViteDevServer() throws Exception {
        mockMvc.perform(options("/api/requests")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }

    @Test
    void cors_shouldRejectThePreflightFromAnyOtherOrigin() throws Exception {
        mockMvc.perform(options("/api/requests")
                        .header("Origin", "http://evil.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }

}
