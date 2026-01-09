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
import java.util.concurrent.TimeUnit;

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
            log.warn("Refresh token request failed: Missing UID or Refresh Token");
            return ResponseEntity.badRequest().body(Map.of("error", "Missing UID or Refresh Token"));
        }

        try {
            // Use CompletableFuture to handle the async Firebase call
            CompletableFuture<DataSnapshot> future = new CompletableFuture<>();
            DatabaseReference ref = FirebaseDatabase.getInstance().getReference("users").child(uid);

            ref.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot dataSnapshot) {
                    future.complete(dataSnapshot);
                }

                @Override
                public void onCancelled(DatabaseError databaseError) {
                    future.completeExceptionally(databaseError.toException());
                }
            });

            // Wait for result with a timeout (e.g., 10 seconds) to prevent hanging
            DataSnapshot snapshot = future.get(10, TimeUnit.SECONDS);

            if (!snapshot.exists()) {
                log.warn("Refresh token failed: User not found in database for UID: {}", uid);
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "User not found"));
            }

            String storedRefreshToken = snapshot.child("refreshToken").getValue(String.class);

            // Validate Refresh Token
            if (storedRefreshToken != null && storedRefreshToken.equals(requestRefreshToken)) {
                log.debug("Refresh token validated successfully for UID: {}", uid);

                // Extract user details safely
                String email = snapshot.child("email").getValue(String.class);
                String role = snapshot.child("role").getValue(String.class);
                String name = snapshot.child("name").getValue(String.class);
                String photoUrl = snapshot.child("profilePhotoUrl").getValue(String.class);

                // Set defaults if null
                role = (role != null) ? role : "CUSTOMER";
                name = (name != null) ? name : "User";
                photoUrl = (photoUrl != null) ? photoUrl : "";

                // Generate New Access Token
                String newAccessToken = jwtService.generateAccessToken(uid, email, role, name, photoUrl);

                log.info("New access token generated for UID: {}", uid);
                return ResponseEntity.ok(Map.of("accessToken", newAccessToken));
            } else {
                log.warn("Refresh token mismatch for UID: {}", uid);
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Invalid Refresh Token"));
            }

        } catch (Exception e) {
            log.error("Error verifying refresh token for UID: {}", uid, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error verifying token: " + e.getMessage()));
        }
    }
}