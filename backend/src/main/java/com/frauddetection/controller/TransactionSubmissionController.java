package com.frauddetection.controller;

import com.frauddetection.dto.CreateTransactionRequest;
import com.frauddetection.dto.TransactionSubmissionResponse;
import com.frauddetection.service.TransactionProcessingService;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/transactions")
public class TransactionSubmissionController {

    private final TransactionProcessingService service;

    public TransactionSubmissionController(
            TransactionProcessingService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionSubmissionResponse submit(
            @Valid @RequestBody CreateTransactionRequest request) {

        return service.process(request);
    }
}
