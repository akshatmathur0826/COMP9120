package com.frauddetection.service;

import com.frauddetection.dto.AlertResponse;
import com.frauddetection.repository.AlertRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AlertService {

    private final AlertRepository repository;

    public AlertService(AlertRepository repository) {
        this.repository = repository;
    }

    public List<AlertResponse> getAlerts() {
        return repository.findRecent();
    }
}
