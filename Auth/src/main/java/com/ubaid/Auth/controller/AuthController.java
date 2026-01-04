package com.ubaid.Auth.controller;

import com.google.firebase.database.*;
import com.ubaid.Auth.service.JwtService;
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
    public ResponseEntity<?> refreshToken(@RequestBody Map<String, String> request) throws ExecutionException, InterruptedException {
        String requestRefreshToken = request.get("refreshToken");
        String uid = request.get("uid");

        if (uid == null || requestRefreshToken == null) {
            return ResponseEntity.badRequest().body("Missing UID or Refresh Token");
        }

        // 1. Fetch stored token AND user details from Firebase
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

        // 2. Validate Refresh Token
        if (storedRefreshToken != null && storedRefreshToken.equals(requestRefreshToken)) {

            // 3. Extract details for new Access Token
            String email = snapshot.child("email").getValue(String.class);
            String role = snapshot.child("role").getValue(String.class);
            if (role == null) role = "CUSTOMER";

            // Get Name and Photo (Handle nulls just in case)
            String name = snapshot.child("name").getValue(String.class);
            if (name == null) name = "User"; // Fallback

            String photoUrl = snapshot.child("profilePhotoUrl").getValue(String.class);
            if (photoUrl == null) photoUrl = ""; // Fallback

            // 4. Generate New Access Token with FULL details
            String newAccessToken = jwtService.generateAccessToken(uid, email, role, name, photoUrl);

            return ResponseEntity.ok(Map.of("accessToken", newAccessToken));
        } else {
            return ResponseEntity.status(403).body("Invalid Refresh Token");
        }
    }
}