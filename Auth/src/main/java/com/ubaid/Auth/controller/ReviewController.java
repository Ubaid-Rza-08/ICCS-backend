package com.ubaid.Auth.controller;

import com.google.firebase.database.*;
import com.google.gson.Gson;
import com.ubaid.Auth.dto.ReviewRequestDto;
import com.ubaid.Auth.model.Review;
import com.ubaid.Auth.model.UserEntity;
import com.ubaid.Auth.service.CloudinaryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
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

    // --- 1. ADD REVIEW ---
    @PostMapping(value = "/add", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Add a new review", description = "Submit a review with a JSON string and optional images", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(
            responseCode = "200",
            description = "Review added successfully",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = Review.class))
    )
    public ResponseEntity<?> addReview(
            @Parameter(description = "JSON string of ReviewRequestDto", schema = @Schema(type = "string", format = "json", example = "{\"productId\":\"ID\",\"rating\":5,\"message\":\"Text\"}"))
            @RequestParam("review") String reviewJson,

            @Parameter(description = "List of image files to upload")
            @RequestParam(value = "images", required = false) List<MultipartFile> files,

            Authentication authentication
    ) {
        try {
            UserEntity currentUser = (UserEntity) authentication.getPrincipal();
            log.info("Received request to add review from user: {}", currentUser.getUsername());

            Gson gson = new Gson();
            ReviewRequestDto requestDto = gson.fromJson(reviewJson, ReviewRequestDto.class);

            if (requestDto.getRating() < 0 || requestDto.getRating() > 5) {
                log.warn("Invalid rating provided: {}", requestDto.getRating());
                return ResponseEntity.badRequest().body("Rating must be between 0 and 5");
            }
            if (requestDto.getProductId() == null || requestDto.getProductId().isEmpty()) {
                log.warn("Missing product ID in review request");
                return ResponseEntity.badRequest().body("Product ID is required");
            }

            List<String> uploadedImageUrls = new ArrayList<>();
            if (files != null && !files.isEmpty()) {
                log.debug("Uploading {} review images...", files.size());
                for (MultipartFile file : files) {
                    if (!file.isEmpty()) {
                        String url = cloudinaryService.uploadImage(file, "reviews");
                        uploadedImageUrls.add(url);
                    }
                }
            }

            Review review = new Review();
            review.setProductId(requestDto.getProductId());
            review.setRating(requestDto.getRating());
            review.setMessage(requestDto.getMessage());
            review.setUserId(currentUser.getId());
            review.setUserName(currentUser.getUsername());
            review.setUserProfileImage(currentUser.getProfilePhotoUrl());
            review.setImageUrls(uploadedImageUrls);
            review.setTimestamp(System.currentTimeMillis());

            DatabaseReference ref = FirebaseDatabase.getInstance().getReference("reviews");
            String reviewId = ref.push().getKey();
            review.setId(reviewId);

            ref.child(reviewId).setValueAsync(review);

            log.info("Review added successfully with ID: {} for Product: {}", reviewId, review.getProductId());
            return ResponseEntity.ok(Map.of("message", "Review added successfully", "review", review));

        } catch (Exception e) {
            log.error("Error adding review", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    // --- 2. GET REVIEWS FOR PRODUCT ---
    @GetMapping("/product/{productId}")
    @Operation(summary = "Get reviews for a product", description = "Fetches all reviews for a specific product ID")
    @ApiResponse(
            responseCode = "200",
            description = "List of reviews",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = Review.class)))
    )
    public CompletableFuture<ResponseEntity<?>> getProductReviews(@PathVariable String productId) {
        log.info("Fetching reviews for product ID: {}", productId);
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
                log.info("Retrieved {} reviews for product {}", reviewList.size(), productId);
                future.complete(ResponseEntity.ok(reviewList));
            }
            @Override
            public void onCancelled(DatabaseError error) {
                log.error("Database error fetching reviews: {}", error.getMessage());
                future.complete(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("Error fetching reviews: " + error.getMessage()));
            }
        });
        return future;
    }
}