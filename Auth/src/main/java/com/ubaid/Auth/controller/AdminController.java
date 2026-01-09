package com.ubaid.Auth.controller;

import com.google.firebase.database.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/admin")
@Slf4j
public class AdminController {

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/promote")
    @Operation(summary = "Promote a user to Seller", description = "Changes a user's role from CUSTOMER to SELLER by email", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "200", description = "User promoted successfully")
    @ApiResponse(responseCode = "404", description = "User not found")
    public ResponseEntity<?> promoteToSeller(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Map containing the email of the user to promote",
                    content = @Content(schema = @Schema(example = "{\"email\": \"user@example.com\"}"))
            )
            @RequestBody Map<String, String> request) {

        String targetEmail = request.get("email");
        log.info("Received request to promote user with email: {}", targetEmail);

        if (targetEmail == null) {
            log.warn("Promotion failed: Email is missing in request body");
            return ResponseEntity.badRequest().body("Email required");
        }

        DatabaseReference usersRef = FirebaseDatabase.getInstance().getReference("users");
        Query query = usersRef.orderByChild("email").equalTo(targetEmail);

        CompletableFuture<ResponseEntity<?>> future = new CompletableFuture<>();

        query.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    for (DataSnapshot userSnapshot : snapshot.getChildren()) {
                        userSnapshot.getRef().child("role").setValueAsync("SELLER");
                        log.info("Successfully promoted user {} to SELLER", targetEmail);
                        future.complete(ResponseEntity.ok("User " + targetEmail + " is now a SELLER."));
                        return;
                    }
                } else {
                    log.warn("Promotion failed: User with email {} not found", targetEmail);
                    future.complete(ResponseEntity.status(404).body("User not found"));
                }
            }
            @Override
            public void onCancelled(DatabaseError error) {
                log.error("Database error during promotion: {}", error.getMessage());
                future.complete(ResponseEntity.internalServerError().body("DB Error"));
            }
        });

        return future.join();
    }
}