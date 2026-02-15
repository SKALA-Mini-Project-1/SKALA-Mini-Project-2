package com.example.SKALA_Mini_Project_1.modules.payments.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.SKALA_Mini_Project_1.modules.payments.domain.Transaction;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
}
