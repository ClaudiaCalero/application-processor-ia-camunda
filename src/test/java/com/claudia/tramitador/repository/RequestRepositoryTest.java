package com.claudia.tramitador.repository;

import com.claudia.tramitador.entity.Request;
import com.claudia.tramitador.enums.RequestStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RequestRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    @Autowired
    private RequestRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void save_shouldApplyTheDefaultValueOfANewRequest() {
        Request saved = repository.saveAndFlush(
                new Request("I received an invoice for 150 euros")
        );
        entityManager.clear();

        Request retrieved = repository.findById(saved.getId()).orElseThrow();

    assertThat(retrieved.getStatus()).isEqualTo(RequestStatus.RECEIVED);
    assertThat(retrieved.isReviewedByPerson()).isFalse();
    assertThat(retrieved.getCreatedAt()).isNotNull();
    assertThat(retrieved.getCategory()).isNull();
    assertThat(retrieved.getScore()).isNull();
    }

    @Test
    void save_shouldStoreTheStatusAsReadableTextAndNotAsAnOrdial() {
        Request request = new Request("text");
        request.setStatus(RequestStatus.IN_REVIEW);
        Request saved = repository.saveAndFlush(request);

        Object stored = entityManager
                .createNativeQuery("select status from request where id = :id")
                .setParameter("id", saved.getId())
                .getSingleResult();

        assertThat(stored).isEqualTo("IN_REVIEW");
    }

    @Test
    void findAll_shouldReturnNewestFirst_whenSortedByCreatedAtDescending() {
        Request oldest = requestCreatedAt("oldest", "2026-10-01T10:00:00Z");
        Request newest = requestCreatedAt("newest", "2026-10-03T10:00:00Z");
        Request middle = requestCreatedAt("middle", "2026-10-02T10:00:00Z");
        repository.saveAllAndFlush(List.of(oldest, newest, middle));

        List<Request> result = repository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));

        assertThat(result)
                .extracting(Request::getText)
                .containsExactly("newest", "middle", "oldest");
    }

    @Test
    void save_shouldFitTextOfExactly5000Characters() {
        Request saved = repository.saveAndFlush(new Request("a".repeat(5000)));
        entityManager.clear();

        assertThat(repository.findById(saved.getId()).orElseThrow().getText()).hasSize(5000);
    }

    @Test
    void save_shouldRejectASummaryLongerThanTheColumn() {
        Request request = new Request("text");
        request.setSummary("s".repeat(2001));

        assertThatThrownBy(() -> repository.saveAndFlush(request))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
    private static Request requestCreatedAt(String text, String instant) {
        Request request = new Request(text);
        request.setCreatedAt(Instant.parse(instant));
        return request;
    }
}
