package com.ubaid.Auth.controller;

import com.google.firebase.database.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
public class UserController {

    // 1. Get Current Logged-in User (Fixed NPE)
    @GetMapping("/api/user")
    public Map<String, Object> getUser(@AuthenticationPrincipal OAuth2User principal) {
        // If user is not logged in, return empty map instead of crashing
        if (principal == null) {
            return Collections.singletonMap("error", "Not logged in");
        }
        return principal.getAttributes();
    }

    // 2. Get ALL Users from Firebase
    @GetMapping("/api/users")
    public List<Map<String, Object>> getAllUsers() throws ExecutionException, InterruptedException {
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("users");

        CompletableFuture<List<Map<String, Object>>> future = new CompletableFuture<>();

        ref.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                List<Map<String, Object>> userList = new ArrayList<>();

                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    // Convert each child node to a Map
                    Map<String, Object> user = (Map<String, Object>) snapshot.getValue();
                    userList.add(user);
                }
                future.complete(userList);
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                future.completeExceptionally(databaseError.toException());
            }
        });

        return future.get(); // Wait for Firebase to return data
    }
}