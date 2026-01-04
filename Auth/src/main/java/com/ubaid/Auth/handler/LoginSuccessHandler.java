package com.ubaid.Auth.handler;

import com.google.firebase.database.*;
import com.ubaid.Auth.service.JwtService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Component
public class LoginSuccessHandler implements AuthenticationSuccessHandler {

    @Autowired
    private JwtService jwtService;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
        OAuth2User oauthUser = (OAuth2User) authentication.getPrincipal();

        // 1. Extract Attributes from Google
        String uid = oauthUser.getAttribute("sub");
        String email = oauthUser.getAttribute("email");
        String name = oauthUser.getAttribute("name");
        String picture = oauthUser.getAttribute("picture");

        // 2. Check Firebase for existing Role
        String role = "CUSTOMER"; // Default
        try {
            role = fetchUserRoleFromFirebase(uid);
        } catch (Exception e) {
            System.out.println("New user or error fetching role, defaulting to CUSTOMER");
        }

        // 3. Generate Token WITH Role AND User Details (Updated Signature)
        // ensure you pass arguments in the order defined in JwtService: (uid, email, role, name, photoUrl)
        String accessToken = jwtService.generateAccessToken(uid, email, role, name, picture);
        String refreshToken = jwtService.generateRefreshToken();

        // 4. Save/Update User in Firebase
        saveUserToFirebase(uid, name, email, picture, role, refreshToken);

        // 5. Redirect to Frontend
        response.sendRedirect("http://localhost:3000/auth/callback?accessToken=" + accessToken +
                "&refreshToken=" + refreshToken +
                "&uid=" + uid +
                "&role=" + role);
    }

    // Helper: Fetch Role synchronously
    private String fetchUserRoleFromFirebase(String uid) throws Exception {
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("users").child(uid);
        CompletableFuture<String> future = new CompletableFuture<>();

        ref.child("role").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    future.complete(snapshot.getValue(String.class));
                } else {
                    future.complete("CUSTOMER"); // Not found = New User
                }
            }
            @Override
            public void onCancelled(DatabaseError error) { future.complete("CUSTOMER"); }
        });
        return future.get(); // Blocks until data returns
    }

    private void saveUserToFirebase(String uid, String name, String email, String picture, String role, String refreshToken) {
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("users");
        Map<String, Object> updates = new HashMap<>();
        updates.put("name", name);
        updates.put("email", email);
        updates.put("profilePhotoUrl", picture); // Changed key to match UserEntity ("profilePhotoUrl")
        updates.put("refreshToken", refreshToken);
        updates.put("role", role);

        ref.child(uid).updateChildrenAsync(updates);
    }
}