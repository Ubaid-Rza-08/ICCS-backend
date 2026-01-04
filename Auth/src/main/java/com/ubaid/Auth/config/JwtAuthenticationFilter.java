package com.ubaid.Auth.config;

import com.ubaid.Auth.model.UserEntity; // Import the new entity
import com.ubaid.Auth.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Autowired
    private JwtService jwtService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");
        String token = null;

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
            try {
                String email = jwtService.extractUsername(token);

                // Check if we have an email and no current auth
                if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {

                    // 1. Extract all details from Token
                    String role = jwtService.extractRole(token);
                    String uid = jwtService.extractUid(token);
                    String name = jwtService.extractName(token);
                    String photo = jwtService.extractPhoto(token);

                    // 2. Create UserEntity Principal
                    UserEntity userEntity = new UserEntity(uid, email, name, photo, role);

                    // 3. Create Authority
                    SimpleGrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + role);

                    // 4. Set Authentication (Pass 'userEntity' as the principal!)
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userEntity, // <--- Principal is now UserEntity, not String
                            null,
                            Collections.singletonList(authority)
                    );

                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            } catch (Exception e) {
                System.out.println("JWT Token invalid or expired: " + e.getMessage());
            }
        }

        filterChain.doFilter(request, response);
    }
}