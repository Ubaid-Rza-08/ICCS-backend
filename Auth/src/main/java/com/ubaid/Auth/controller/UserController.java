package com.ubaid.Auth.controller;

import com.google.firebase.database.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class UserController {

    @GetMapping("/api/user")
    @Operation(summary = "Get current user info", description = "Retrieves details of the currently logged-in user (OAuth2)", security = @SecurityRequirement(name = "bearerAuth"))
    public Map<String, Object> getUser(
            @Parameter(hidden = true) // Hide this from Swagger as it is injected automatically
            @AuthenticationPrincipal OAuth2User principal
    ) {
        log.info("Request received to get current user info");
        if (principal == null) {
            log.warn("getUser request failed: Principal is null (Not logged in)");
            return Collections.singletonMap("error", "Not logged in");
        }
        log.debug("Returning details for user: {}", principal.getName());
        return principal.getAttributes();
    }

    @GetMapping("/api/users")
    @Operation(summary = "Get all users", description = "Retrieves a list of all users from Firebase", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(
            responseCode = "200",
            description = "List of users",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = Map.class))
    )
    public List<Map<String, Object>> getAllUsers() throws ExecutionException, InterruptedException {
        log.info("Request received to fetch all users");
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("users");
        CompletableFuture<List<Map<String, Object>>> future = new CompletableFuture<>();

        ref.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                List<Map<String, Object>> userList = new ArrayList<>();
                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    Map<String, Object> user = (Map<String, Object>) snapshot.getValue();
                    userList.add(user);
                }
                log.info("Successfully retrieved {} users", userList.size());
                future.complete(userList);
            }
            @Override
            public void onCancelled(DatabaseError databaseError) {
                log.error("Error fetching all users: {}", databaseError.getMessage());
                future.completeExceptionally(databaseError.toException());
            }
        });

        return future.get();
    }
}