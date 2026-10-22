package com.frauddetection.service;

import com.frauddetection.dto.ReferenceDataResponse;
import com.frauddetection.repository.ReferenceDataRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReferenceDataService {

    private final ReferenceDataRepository repository;

    public ReferenceDataService(ReferenceDataRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public ReferenceDataResponse getReferenceData() {
        return new ReferenceDataResponse(
                repository.findCards(),
                repository.findMerchants(),
                repository.findDevices(),
                repository.findLocations(),
                repository.findActiveAnalysts(),
                repository.findRules()
        );
    }
}
