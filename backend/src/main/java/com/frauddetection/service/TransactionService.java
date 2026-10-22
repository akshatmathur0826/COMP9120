package com.frauddetection.service;

import com.frauddetection.dto.TransactionResponse;
import com.frauddetection.repository.TransactionRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TransactionService {

    private final TransactionRepository transactionRepository;

    public TransactionService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    public List<TransactionResponse> getRecentTransactions() {
        return transactionRepository.findRecent(100);
    }
}
