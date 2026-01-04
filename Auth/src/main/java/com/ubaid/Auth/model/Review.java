package com.ubaid.Auth.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Review {
    private String id;
    private String productId;

    // User Details (Snapshot at time of review)
    private String userId;
    private String userName;
    private String userProfileImage;

    // Review Content
    private int rating; // 0 to 5
    private String message;
    private List<String> imageUrls = new ArrayList<>();

    private long timestamp;
}