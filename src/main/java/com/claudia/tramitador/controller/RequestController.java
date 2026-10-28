package com.claudia.tramitador.controller;

import com.claudia.tramitador.dto.CreateRequest;
import com.claudia.tramitador.entity.Request;
import com.claudia.tramitador.repository.RequestRepository;
import io.camunda.client.CamundaClient;
import jakarta.validation.Valid;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/requests")
@CrossOrigin(origins = "http://localhost:5173")
public class RequestController {

    private final RequestRepository repository;
    private final CamundaClient camunda;

    public RequestController(RequestRepository repository, CamundaClient camunda) {
        this.repository = repository;
        this.camunda = camunda;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Request createRequest(@Valid @RequestBody CreateRequest request) {
        Request req = repository.save(new Request(request.text()));
        camunda.newCreateInstanceCommand()
                .bpmnProcessId("process-request")
                .latestVersion()
                .variables(Map.of("requestId", req.getId()))
                .send()
                .join();
        return req;
    }

    @GetMapping
    public List<Request> list() {
        return repository.findAll(Sort.by(Sort.Direction.DESC, "created"));
    }
}
