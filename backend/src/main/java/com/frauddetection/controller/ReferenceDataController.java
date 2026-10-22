package com.frauddetection.controller;

import com.frauddetection.dto.ReferenceDataResponse;
import com.frauddetection.service.ReferenceDataService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reference")
public class ReferenceDataController {

    private final ReferenceDataService service;

    public ReferenceDataController(ReferenceDataService service) {
        this.service = service;
    }

    @GetMapping
    public ReferenceDataResponse getReferenceData() {
        return service.getReferenceData();
    }
}
