package com.ubaid.Auth.config;

import com.ubaid.Auth.handler.LoginSuccessHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus; // <--- REQUIRED IMPORT
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint; // <--- REQUIRED IMPORT
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Autowired
    private LoginSuccessHandler loginSuccessHandler;

    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // 1. CORS Configuration
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // 2. Disable CSRF (Stateless JWT)
                .csrf(AbstractHttpConfigurer::disable)

                // 3. Set Session Management to STATELESS
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // -------------------------------------------------------------------
                // 4. FIX: Handle Auth Errors with 401 (Unauthorized) instead of Redirect
                // This prevents the CORS error when the token expires.
                // -------------------------------------------------------------------
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                )

                // 5. Define Endpoint Authorization
                .authorizeHttpRequests(auth -> auth
                        // --- PUBLIC ENDPOINTS ---
                        .requestMatchers(
                                "/",
                                "/index.html",
                                "/login/**",
                                "/api/auth/**",
                                "/api/user",
                                "/api/users"
                        ).permitAll()

                        // --- SWAGGER UI WHITELIST ---
                        .requestMatchers(
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html"
                        ).permitAll()

                        // --- PUBLIC PRODUCT BROWSING ---
                        .requestMatchers("/api/public/**").permitAll()

                        // --- PUBLIC REVIEWS (READ-ONLY) ---
                        .requestMatchers(HttpMethod.GET, "/api/reviews/**").permitAll()

                        // --- ADMIN ONLY ---
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")

                        // --- SELLER ONLY ---
                        .requestMatchers("/api/seller/**", "/api/analyze-product").hasRole("SELLER")

                        // --- ALL OTHER REQUESTS REQUIRE AUTH ---
                        .anyRequest().authenticated()
                )

                // 6. Add JWT Filter
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)

                // 7. OAuth2 Login
                .oauth2Login(oauth -> oauth
                        .successHandler(loginSuccessHandler)
                );

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // Allowed Origins
        configuration.setAllowedOrigins(List.of(
                "http://localhost:5173",
                "http://localhost:3000",
                "http://127.0.0.1:5500"
        ));

        // Allowed Methods
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));

        // Allowed Headers
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With", "Accept"));

        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}