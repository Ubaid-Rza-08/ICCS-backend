package com.ubaid.Auth.model;

import lombok.Data; // or generate Getters/Setters manually

@Data
public class Payment {
    private String orderId;
    private String paymentId;
    private String signature;
    private String status;       // CREATED, PAID
    private int amount;
    private String productId;
    private String customerId;
    private long createdAt;
}