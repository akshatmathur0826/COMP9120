package com.frauddetection.controller;

import com.frauddetection.dto.AlertDetailResponse;
import com.frauddetection.dto.AlertReviewResponse;
import com.frauddetection.dto.UpdateAlertStatusRequest;
import com.frauddetection.service.AlertReviewService;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/alerts")
public class AlertReviewController {

    private final AlertReviewService service;

    public AlertReviewController(AlertReviewService service) {
        this.service = service;
    }

    @PatchMapping("/{id}/status")
    public AlertDetailResponse updateStatus(
            @PathVariable("id") Long id,
            @Valid @RequestBody UpdateAlertStatusRequest request) {

        return service.updateStatus(id, request);
    }

    @GetMapping("/{id}/reviews")
    public List<AlertReviewResponse> getReviews(
            @PathVariable("id") Long id) {

        return service.getReviews(id);
    }
}
