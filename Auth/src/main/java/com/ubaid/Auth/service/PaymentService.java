package com.ubaid.Auth.service;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.Utils;
import com.ubaid.Auth.model.Payment;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    @Value("${razorpay.key.id}")
    private String keyId;

    @Value("${razorpay.key.secret}")
    private String keySecret;

    // 1. Create Order & Save to Realtime Database
    public String createOrder(int amount, String productId, String customerId) throws Exception {
        log.info("Initiating Order Creation | Customer: {} | Product: {} | Amount: ₹{}", customerId, productId, amount);

        try {
            RazorpayClient client = new RazorpayClient(keyId, keySecret);

            // --- Razorpay Logic ---
            JSONObject orderRequest = new JSONObject();
            orderRequest.put("amount", amount * 100); // Amount in paise
            orderRequest.put("currency", "INR");
            orderRequest.put("receipt", "txn_" + System.currentTimeMillis());

            JSONObject notes = new JSONObject();
            notes.put("product_id", productId);
            notes.put("customer_id", customerId);
            orderRequest.put("notes", notes);

            Order order = client.orders.create(orderRequest);
            String razorpayOrderId = order.get("id");
            log.info("Razorpay Order Created Successfully | Order ID: {}", razorpayOrderId);

            // --- Firebase Realtime Database Logic ---
            Payment payment = new Payment();
            payment.setOrderId(razorpayOrderId);
            payment.setAmount(amount);
            payment.setProductId(productId);
            payment.setCustomerId(customerId);
            payment.setStatus("CREATED");
            payment.setCreatedAt(System.currentTimeMillis());

            // Get Reference to "payments" node
            DatabaseReference ref = FirebaseDatabase.getInstance().getReference("payments");

            // Save data under the Razorpay Order ID
            ref.child(razorpayOrderId).setValueAsync(payment);
            log.info("Payment Record Saved to Firebase | Status: CREATED");

            return order.toString();

        } catch (Exception e) {
            log.error("Error while creating order for Customer: {}", customerId, e);
            throw e;
        }
    }

    // 2. Verify Signature & Update Realtime Database
    public boolean verifyPayment(String orderId, String paymentId, String signature) throws Exception {
        log.info("Verifying Payment | Order ID: {} | Payment ID: {}", orderId, paymentId);

        try {
            JSONObject options = new JSONObject();
            options.put("razorpay_order_id", orderId);
            options.put("razorpay_payment_id", paymentId);
            options.put("razorpay_signature", signature);

            boolean isValid = Utils.verifyPaymentSignature(options, keySecret);
            log.info("Signature Verification Result: {}", isValid);

            DatabaseReference ref = FirebaseDatabase.getInstance().getReference("payments");

            if (isValid) {
                // --- Update Status to PAID ---
                Map<String, Object> updates = new HashMap<>();
                updates.put("paymentId", paymentId);
                updates.put("signature", signature);
                updates.put("status", "PAID");

                // Update specific fields for this order
                ref.child(orderId).updateChildrenAsync(updates);
                log.info("Firebase Updated | Order ID: {} | Status: PAID", orderId);
            } else {
                // Optional: Mark as FAILED
                ref.child(orderId).child("status").setValueAsync("FAILED");
                log.warn("Payment Verification Failed | Order ID: {} | Marked as FAILED in DB", orderId);
            }

            return isValid;

        } catch (Exception e) {
            log.error("Exception during payment verification | Order ID: {}", orderId, e);
            throw e;
        }
    }
}