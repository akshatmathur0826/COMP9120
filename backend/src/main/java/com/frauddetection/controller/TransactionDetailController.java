package com.frauddetection.controller;

import com.frauddetection.dto.TransactionDetailResponse;
import com.frauddetection.service.TransactionDetailService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/transactions")
public class TransactionDetailController {

    private final TransactionDetailService service;

    public TransactionDetailController(TransactionDetailService service) {
        this.service = service;
    }

    @GetMapping("/{id}")
    public TransactionDetailResponse getTransaction(
            @PathVariable("id") Long id) {

        return service.getTransaction(id);
    }
}
