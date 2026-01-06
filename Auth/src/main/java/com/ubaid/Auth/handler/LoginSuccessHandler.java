package com.ubaid.Auth.handler;

import com.google.firebase.database.*;
import com.ubaid.Auth.model.UserEntity;
import com.ubaid.Auth.service.JwtService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
@Slf4j
public class LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final JwtService jwtService;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
        try {
            OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
            String email = oAuth2User.getAttribute("email");
            String name = oAuth2User.getAttribute("name");
            String photoUrl = oAuth2User.getAttribute("picture");

            // 1. Get User (Robust method)
            UserEntity user = getOrCreateUser(email, name, photoUrl);

            // Safety Check
            if (user.getId() == null) {
                log.error("User ID is NULL for email: " + email);
                response.sendRedirect("http://localhost:5173?error=UserError");
                return;
            }

            // 2. Generate Tokens
            String accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail(), user.getRole(), user.getUsername(), user.getProfilePhotoUrl());
            String refreshToken = UUID.randomUUID().toString();

            // 3. Save Refresh Token
            saveRefreshToken(user.getId(), refreshToken);

            // 4. Redirect
            String targetUrl = "http://localhost:5173/auth/callback" +
                    "?accessToken=" + accessToken +
                    "&refreshToken=" + refreshToken +
                    "&uid=" + user.getId() +
                    "&role=" + (user.getRole() != null ? user.getRole() : "CUSTOMER");

            response.sendRedirect(targetUrl);

        } catch (Exception e) {
            log.error("Login Success Handler Error", e);
            response.sendRedirect("http://localhost:5173?error=LoginFailed");
        }
    }

    private UserEntity getOrCreateUser(String email, String name, String photoUrl) throws Exception {
        DatabaseReference usersRef = FirebaseDatabase.getInstance().getReference("users");
        CompletableFuture<UserEntity> future = new CompletableFuture<>();

        Query query = usersRef.orderByChild("email").equalTo(email);

        query.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    for (DataSnapshot child : snapshot.getChildren()) {
                        // FIX: Get UID from the Key directly (Guaranteed to exist)
                        String uid = child.getKey();

                        // Try to map object
                        UserEntity existingUser = child.getValue(UserEntity.class);

                        // Fallback if mapping failed due to field mismatch
                        if (existingUser == null) existingUser = new UserEntity();
                        if (existingUser.getId() == null) existingUser.setId(uid);
                        if (existingUser.getEmail() == null) existingUser.setEmail(email);
                        if (existingUser.getUsername() == null) existingUser.setUsername(name);
                        if (existingUser.getProfilePhotoUrl() == null) existingUser.setProfilePhotoUrl(photoUrl);
                        if (existingUser.getRole() == null) existingUser.setRole("CUSTOMER");

                        future.complete(existingUser);
                        return;
                    }
                } else {
                    // Create New User
                    String newUid = UUID.randomUUID().toString();
                    UserEntity newUser = new UserEntity(newUid, email, name, photoUrl, "CUSTOMER");
                    usersRef.child(newUid).setValueAsync(newUser);
                    future.complete(newUser);
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
                future.completeExceptionally(new RuntimeException(error.getMessage()));
            }
        });

        return future.get();
    }

    private void saveRefreshToken(String uid, String refreshToken) {
        if (uid == null) {
            log.error("Cannot save refresh token: UID is null");
            return;
        }
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("users").child(uid);
        ref.child("refreshToken").setValueAsync(refreshToken);
    }
}