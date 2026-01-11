package com.ubaid.Auth.dto;

public class PaymentRequest {
    private int amount;
    private String productId;

    // Getters and Setters
    public int getAmount() { return amount; }
    public void setAmount(int amount) { this.amount = amount; }

    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }
}