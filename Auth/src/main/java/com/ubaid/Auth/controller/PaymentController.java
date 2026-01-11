package com.ubaid.Auth.controller;

import com.ubaid.Auth.dto.PaymentRequest;
import com.ubaid.Auth.service.PaymentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/payment")
@CrossOrigin // Allow frontend access
public class PaymentController {

    @Autowired
    private PaymentService paymentService;

    // Step 1: Create Order
    @PostMapping(value = "/create-order", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createOrder(@RequestBody PaymentRequest request) {
        try {
            // 1. Get the authenticated user from the Token
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String customerId = authentication.getName();

            // Check if user is logged in
            if (customerId == null || !authentication.isAuthenticated()) {
                return ResponseEntity.status(401).body("{\"error\": \"User not authenticated\"}");
            }

            // 2. Call Service
            String razorpayOrder = paymentService.createOrder(
                    request.getAmount(),
                    request.getProductId(),
                    customerId
            );

            return ResponseEntity.ok(razorpayOrder);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body("{\"error\": \"Error creating order: " + e.getMessage() + "\"}");
        }
    }

    // Step 2: Verify Payment
    @PostMapping("/verify")
    public ResponseEntity<String> verifyPayment(@RequestBody Map<String, String> data) {
        try {
            String orderId = data.get("razorpay_order_id");
            String paymentId = data.get("razorpay_payment_id");
            String signature = data.get("razorpay_signature");

            boolean isValid = paymentService.verifyPayment(orderId, paymentId, signature);

            if (isValid) {
                return ResponseEntity.ok("Payment Successful");
            } else {
                return ResponseEntity.badRequest().body("Invalid Signature");
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error verifying payment");
        }
    }
}