package com.ubaid.Auth.controller;

import com.google.firebase.database.*;
import com.ubaid.Auth.service.JwtService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@RestController
@RequestMapping("/api/auth")
@Slf4j
public class AuthController {

    @Autowired
    private JwtService jwtService;

    @PostMapping("/refresh")
    @Operation(summary = "Refresh Access Token", description = "Generates a new Access Token using a valid Refresh Token")
    @ApiResponse(
            responseCode = "200",
            description = "Token refreshed successfully",
            content = @Content(schema = @Schema(example = "{\"accessToken\": \"eyJhbG...\"}"))
    )
    public ResponseEntity<?> refreshToken(@RequestBody Map<String, String> request) {

        String requestRefreshToken = request.get("refreshToken");
        String uid = request.get("uid");

        log.info("Request received to refresh token for UID: {}", uid);

        if (uid == null || requestRefreshToken == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing UID or Refresh Token"));
        }

        try {
            DatabaseReference ref = FirebaseDatabase.getInstance().getReference("users").child(uid);

            // 1. Create a Future to wait for the data
            CompletableFuture<DataSnapshot> future = new CompletableFuture<>();

            // 2. Attach Listener
            ref.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot dataSnapshot) {
                    // Success: Pass data to future
                    future.complete(dataSnapshot);
                }

                @Override
                public void onCancelled(DatabaseError databaseError) {
                    // Error: Pass exception to future
                    future.completeExceptionally(databaseError.toException());
                }
            });

            // 3. Wait for result (10 seconds max)
            DataSnapshot snapshot = future.get(10, TimeUnit.SECONDS);

            if (!snapshot.exists()) {
                log.warn("Refresh token failed: User not found for UID: {}", uid);
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "User not found"));
            }

            // 4. MANUAL MAPPING (No UserEntity needed)
            String storedRefreshToken = snapshot.child("refreshToken").getValue(String.class);
            String email = snapshot.child("email").getValue(String.class);
            String name = snapshot.child("name").getValue(String.class);
            String role = snapshot.child("role").getValue(String.class);

            // Check 'profilePhotoUrl' first, then 'picture'
            String photoUrl = snapshot.child("profilePhotoUrl").getValue(String.class);
            if (photoUrl == null) {
                photoUrl = snapshot.child("picture").getValue(String.class);
            }

            // Validate
            if (storedRefreshToken != null && storedRefreshToken.equals(requestRefreshToken)) {

                // Defaults
                role = (role != null) ? role : "CUSTOMER";
                name = (name != null) ? name : "User";
                photoUrl = (photoUrl != null) ? photoUrl : "";

                // Generate Token
                String newAccessToken = jwtService.generateAccessToken(uid, email, role, name, photoUrl);

                log.info("Token refreshed successfully for UID: {}", uid);
                return ResponseEntity.ok(Map.of("accessToken", newAccessToken));

            } else {
                log.warn("Invalid refresh token for UID: {}", uid);
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Invalid Refresh Token"));
            }

        } catch (TimeoutException e) {
            log.error("Firebase connection timed out for UID: {}. CHECK YOUR DATABASE URL.", uid);
            return ResponseEntity.status(HttpStatus.REQUEST_TIMEOUT).body(Map.of("error", "Database timeout. Check server configuration."));
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error executing Firebase request for UID: {}", uid, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Internal Server Error"));
        } catch (Exception e) {
            log.error("Unexpected error for UID: {}", uid, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Unexpected error"));
        }
    }
}