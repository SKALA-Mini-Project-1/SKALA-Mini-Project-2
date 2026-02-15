package com.example.SKALA_Mini_Project_1.modules.payments.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 우선은 단순히 paymentId로 연결(나중에 @ManyToOne으로 바꿔도 됨)
    private Long paymentId;

    private String pgTransactionId;
    private String approvalCode;

    private Integer amount;

    private LocalDateTime approvedAt;

    // TODO: getter/setter 또는 Lombok 사용
}
