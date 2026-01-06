package com.ubaid.Auth.controller;

import com.google.firebase.database.*;
import com.ubaid.Auth.service.JwtService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api/auth")
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
    public ResponseEntity<?> refreshToken(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Request body containing uid and refreshToken",
                    content = @Content(schema = @Schema(example = "{\"uid\": \"user123\", \"refreshToken\": \"uuid-token...\"}"))
            )
            @RequestBody Map<String, String> request) throws ExecutionException, InterruptedException {

        String requestRefreshToken = request.get("refreshToken");
        String uid = request.get("uid");

        if (uid == null || requestRefreshToken == null) {
            return ResponseEntity.badRequest().body("Missing UID or Refresh Token");
        }

        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("users");
        CompletableFuture<DataSnapshot> future = new CompletableFuture<>();

        ref.child(uid).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                future.complete(dataSnapshot);
            }
            @Override
            public void onCancelled(DatabaseError databaseError) {
                future.completeExceptionally(databaseError.toException());
            }
        });

        DataSnapshot snapshot = future.get();
        if (!snapshot.exists()) return ResponseEntity.status(403).body("User not found");

        String storedRefreshToken = snapshot.child("refreshToken").getValue(String.class);

        if (storedRefreshToken != null && storedRefreshToken.equals(requestRefreshToken)) {
            String email = snapshot.child("email").getValue(String.class);
            String role = snapshot.child("role").getValue(String.class);
            if (role == null) role = "CUSTOMER";

            String name = snapshot.child("name").getValue(String.class);
            if (name == null) name = "User";

            String photoUrl = snapshot.child("profilePhotoUrl").getValue(String.class);
            if (photoUrl == null) photoUrl = "";

            String newAccessToken = jwtService.generateAccessToken(uid, email, role, name, photoUrl);
            return ResponseEntity.ok(Map.of("accessToken", newAccessToken));
        } else {
            return ResponseEntity.status(403).body("Invalid Refresh Token");
        }
    }
}