package com.ubaid.Auth.controller;

import com.google.firebase.database.*;
import com.google.gson.Gson; // Import Gson
import com.ubaid.Auth.model.Review; // Ensure this imports your Review model
import com.ubaid.Auth.model.UserEntity;
import com.ubaid.Auth.service.CloudinaryService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
@Slf4j
public class ReviewController {

    private final CloudinaryService cloudinaryService;

    // --- 1. ADD REVIEW (Updated to accept JSON String + Files) ---
    @PostMapping(value = "/add", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> addReview(
            @RequestParam("review") String reviewJson, // <--- Accepts JSON String
            @RequestParam(value = "images", required = false) List<MultipartFile> files,
            Authentication authentication
    ) {
        try {
            // 1. Parse JSON to DTO/Object
            Gson gson = new Gson();
            ReviewRequestDto requestDto = gson.fromJson(reviewJson, ReviewRequestDto.class);

            // 2. Validate Input
            if (requestDto.getRating() < 0 || requestDto.getRating() > 5) {
                return ResponseEntity.badRequest().body("Rating must be between 0 and 5");
            }
            if (requestDto.getProductId() == null || requestDto.getProductId().isEmpty()) {
                return ResponseEntity.badRequest().body("Product ID is required");
            }

            // 3. Get User Details from Token
            UserEntity currentUser = (UserEntity) authentication.getPrincipal();

            // 4. Handle Image Uploads
            List<String> uploadedImageUrls = new ArrayList<>();
            if (files != null && !files.isEmpty()) {
                for (MultipartFile file : files) {
                    if (!file.isEmpty()) {
                        String url = cloudinaryService.uploadImage(file, "reviews");
                        uploadedImageUrls.add(url);
                    }
                }
            }

            // 5. Construct Review Object
            Review review = new Review();
            review.setProductId(requestDto.getProductId());
            review.setRating(requestDto.getRating());
            review.setMessage(requestDto.getMessage());

            review.setUserId(currentUser.getId());
            review.setUserName(currentUser.getUsername());
            review.setUserProfileImage(currentUser.getProfilePhotoUrl());
            review.setImageUrls(uploadedImageUrls);
            review.setTimestamp(System.currentTimeMillis());

            // 6. Save to Firebase
            DatabaseReference ref = FirebaseDatabase.getInstance().getReference("reviews");
            String reviewId = ref.push().getKey();
            review.setId(reviewId);

            ref.child(reviewId).setValueAsync(review);

            return ResponseEntity.ok(Map.of("message", "Review added successfully", "review", review));

        } catch (Exception e) {
            log.error("Error adding review", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    // --- 2. GET REVIEWS FOR PRODUCT (No Change) ---
    @GetMapping("/product/{productId}")
    public CompletableFuture<ResponseEntity<?>> getProductReviews(@PathVariable String productId) {
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("reviews");
        CompletableFuture<ResponseEntity<?>> future = new CompletableFuture<>();

        Query query = ref.orderByChild("productId").equalTo(productId);

        query.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                List<Review> reviewList = new ArrayList<>();
                for (DataSnapshot data : snapshot.getChildren()) {
                    Review review = data.getValue(Review.class);
                    if (review != null) reviewList.add(review);
                }
                reviewList.sort((r1, r2) -> Long.compare(r2.getTimestamp(), r1.getTimestamp()));
                future.complete(ResponseEntity.ok(reviewList));
            }
            @Override
            public void onCancelled(DatabaseError error) {
                future.complete(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("Error fetching reviews: " + error.getMessage()));
            }
        });
        return future;
    }

    // --- DTO Class (Inner Class or Separate File) ---
    @Data
    static class ReviewRequestDto {
        private String productId;
        private int rating;
        private String message;
    }
}