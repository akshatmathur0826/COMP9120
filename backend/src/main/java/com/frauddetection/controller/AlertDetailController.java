package com.frauddetection.controller;

import com.frauddetection.dto.AlertDetailResponse;
import com.frauddetection.service.AlertDetailService;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/alerts")
public class AlertDetailController {

    private final AlertDetailService service;

    public AlertDetailController(AlertDetailService service) {
        this.service = service;
    }

    @GetMapping("/{id}")
    public AlertDetailResponse getAlert(
            @PathVariable("id") Long id) {

        return service.getAlert(id);
    }
}
