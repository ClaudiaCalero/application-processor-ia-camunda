package com.claudia.tramitador.service;

import com.claudia.tramitador.entity.Request;
import com.claudia.tramitador.enums.RequestStatus;
import com.claudia.tramitador.model.Classification;
import com.claudia.tramitador.repository.RequestRepository;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.api.response.ActivatedJob;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class RequestWorkers {
    private final RequestRepository repository;
    private final AIClassifier aiClassifier;

    @Value("${tramitador.umbral:0.8}")
    private double threshold;
    public RequestWorkers(RequestRepository repository, AIClassifier aiClassifier) {
        this.repository = repository;
        this.aiClassifier = aiClassifier;
    }

    @JobWorker(type = "classify-application", fetchAllVariables = true)
    public Map<String, Object> classifyRequest(ActivatedJob job) {
        Map<String, Object> variables = job.getVariablesAsMap();
        Long requestId = ((Number) variables.get("requestId")).longValue();

        Request request = repository.findById(requestId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Request not found: " + requestId
                        )
                );

        Classification result = aiClassifier.classify(request.getText());
        boolean reviewRequired =
                result.score() < threshold
                || result.category().name().equals("OTHER");

        request.setCategory(result.category().name());
        request.setScore(result.score());
        request.setSummary(result.summary());
        request.setAmount(result.amount());
        request.setStatus(
                reviewRequired
                ? RequestStatus.IN_REVIEW
                        : RequestStatus.RECEIVED
        );

        repository.save(request);
        Map<String, Object> resultVariables = new HashMap<>();
        resultVariables.put("category", result.category().name());
        resultVariables.put("score", result.score());
        resultVariables.put("reviewRequired", reviewRequired);

        return resultVariables;
    }

    @JobWorker(type = "finalize-application", fetchAllVariables = true)
    public void finalizeRequest(ActivatedJob job) {
        Map<String, Object> variables = job.getVariablesAsMap();
        Long requestId = ((Number) variables.get("requestId")).longValue();

        Request request = repository.findById(requestId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Request not found: " + requestId
                        )
                );

        Object category = variables.get("category");
        if(category != null) {
            request.setCategory(category.toString());
        }

        Object reviewed = variables.get("reviewedByPerson");
        request.setReviewedByPerson(
                Boolean.TRUE.equals(reviewed)
        );

        request.setStatus(RequestStatus.COMPLETED);
        repository.save(request);
    }
}
