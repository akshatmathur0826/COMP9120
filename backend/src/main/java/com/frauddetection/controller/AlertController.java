package com.frauddetection.controller;

import com.frauddetection.dto.AlertResponse;
import com.frauddetection.service.AlertService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    private final AlertService service;

    public AlertController(AlertService service) {
        this.service = service;
    }

    @GetMapping
    public List<AlertResponse> getAlerts() {
        return service.getAlerts();
    }
}
