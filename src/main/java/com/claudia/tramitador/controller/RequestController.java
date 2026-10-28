package com.claudia.tramitador.controller;

import com.claudia.tramitador.entity.Request;
import com.claudia.tramitador.repository.RequestRepository;
import jakarta.validation.Valid;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/requests")
public class RequestController {

    private final RequestRepository requestRepository;

    public RequestController(RequestRepository requestRepository) {
        this.requestRepository = requestRepository;
    }

    @GetMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Request createRequest(@Valid @RequestBody CreateRequest body) {
        Request request = new Request();
        request.setText(body.text().trim());

        return requestRepository.save(request);
    }
}
