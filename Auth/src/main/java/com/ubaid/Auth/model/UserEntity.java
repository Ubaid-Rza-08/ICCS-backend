package com.ubaid.Auth.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserEntity {
    private String id;              // The Firebase UID
    private String email;
    private String username;        // Display Name
    private String profilePhotoUrl;
    private String role;            // e.g., "SELLER", "CUSTOMER"
}