package com.example.SKALA_Mini_Project_1.modules.payments.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.SKALA_Mini_Project_1.modules.payments.domain.Refund;

public interface RefundRepository extends JpaRepository<Refund, UUID> {
    Optional<Refund> findTopByPaymentIdOrderByCreatedAtDesc(UUID paymentId);

    List<Refund> findTop50ByStatusOrderByCreatedAtAsc(String status);
}

