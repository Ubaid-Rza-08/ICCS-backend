package com.ubaid.Auth.controller;

import com.google.firebase.database.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    // Only Users with ROLE_ADMIN can access this
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/promote")
    public ResponseEntity<?> promoteToSeller(@RequestBody Map<String, String> request) {
        String targetEmail = request.get("email");

        if (targetEmail == null) return ResponseEntity.badRequest().body("Email required");

        // 1. Find UID by Email (Since Firebase keys are UIDs, we must query)
        DatabaseReference usersRef = FirebaseDatabase.getInstance().getReference("users");

        Query query = usersRef.orderByChild("email").equalTo(targetEmail);

        CompletableFuture<ResponseEntity<?>> future = new CompletableFuture<>();

        query.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    for (DataSnapshot userSnapshot : snapshot.getChildren()) {
                        // Found the user, update role
                        userSnapshot.getRef().child("role").setValueAsync("SELLER");
                        future.complete(ResponseEntity.ok("User " + targetEmail + " is now a SELLER."));
                        return;
                    }
                } else {
                    future.complete(ResponseEntity.status(404).body("User not found"));
                }
            }
            @Override
            public void onCancelled(DatabaseError error) {
                future.complete(ResponseEntity.internalServerError().body("DB Error"));
            }
        });

        return future.join();
    }
}