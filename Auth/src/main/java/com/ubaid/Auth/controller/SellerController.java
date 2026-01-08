package com.ubaid.Auth.controller;

import com.google.firebase.database.*;
import com.google.gson.Gson;
import com.ubaid.Auth.dto.SellerProductResponseDto;
import com.ubaid.Auth.model.Product;
import com.ubaid.Auth.service.CloudinaryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api/seller")
@RequiredArgsConstructor
@Slf4j
public class SellerController {

    private final CloudinaryService cloudinaryService;

    // --- CREATE PRODUCT ---
    @PreAuthorize("hasRole('SELLER')")
    @PostMapping(value = "/create-product", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Create a new product", description = "Allows a seller to create a product with images", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(
            responseCode = "200",
            description = "Product created successfully",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = Map.class))
    )
    public ResponseEntity<?> createProduct(
            @RequestParam("product") String productJson,
            @RequestParam(value = "images", required = false) List<MultipartFile> files
    ) {
        Map<String, Object> response = new HashMap<>();
        try {
            // 1. Parse JSON
            Gson gson = new Gson();
            Product product = gson.fromJson(productJson, Product.class);

            // 2. Set Seller Email
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String currentSellerEmail = auth.getName();
            product.setSellerEmail(currentSellerEmail);

            // 3. IMAGE HANDLING (MERGE JSON URLS + NEW FILES)
            List<String> finalImages = new ArrayList<>();

            // A. Add existing images from JSON (if any)
            if (product.getpImages() != null) {
                finalImages.addAll(product.getpImages());
            }

            // B. Upload new files and add them
            if (files != null && !files.isEmpty()) {
                for (MultipartFile file : files) {
                    if (file.isEmpty()) continue;
                    // Check size limit (5MB)
                    if (!cloudinaryService.isValidFileSize(file, 5.0)) {
                        return ResponseEntity.badRequest().body(Map.of("error", "File " + file.getOriginalFilename() + " exceeds 5MB limit"));
                    }
                    String url = cloudinaryService.uploadProductImage(file);
                    finalImages.add(url);
                }
            }
            product.setpImages(finalImages);

            // 4. Handle other defaults
            if (product.getKeywords() == null) product.setKeywords(new ArrayList<>());

            // 5. Generate ID
            String customId = generateProductId(product.getpName(), product.getpBrandName());
            product.setpId(customId);

            // 6. Save to Firebase
            DatabaseReference ref = FirebaseDatabase.getInstance().getReference("products");
            ref.child(customId).setValueAsync(product);

            response.put("message", "Product created successfully");
            response.put("productId", customId);
            response.put("imageCount", finalImages.size());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error creating product: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    // --- GET ALL PRODUCTS (SELLER SPECIFIC) ---
    @GetMapping("/all")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<List<SellerProductResponseDto>> getAllProducts() throws Exception {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String sellerEmail = auth.getName();

        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("products");
        CompletableFuture<List<SellerProductResponseDto>> future = new CompletableFuture<>();

        ref.orderByChild("sellerEmail").equalTo(sellerEmail)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        List<SellerProductResponseDto> list = new ArrayList<>();
                        for (DataSnapshot data : snapshot.getChildren()) {
                            Product p = data.getValue(Product.class);
                            if (p != null) {
                                list.add(mapToSellerDto(p));
                            }
                        }
                        future.complete(list);
                    }

                    @Override
                    public void onCancelled(DatabaseError error) {
                        future.completeExceptionally(
                                new RuntimeException(error.getMessage())
                        );
                    }
                });

        return ResponseEntity.ok(future.get());
    }

    // --- DELETE PRODUCT ---
    @PreAuthorize("hasRole('SELLER')")
    @DeleteMapping("/delete/{pId}")
    @Operation(summary = "Delete a product", description = "Deletes a specific product by ID (Owner only)", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "200", description = "Product deleted successfully")
    @ApiResponse(responseCode = "403", description = "Forbidden: You do not own this product")
    public ResponseEntity<?> deleteProduct(@PathVariable String pId) {
        try {
            Product existingProduct = getProductSync(pId);
            if (existingProduct == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Product not found"));

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (!existingProduct.getSellerEmail().equals(auth.getName())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "You do not own this product"));
            }

            // Delete images from Cloudinary
            if (existingProduct.getpImages() != null && !existingProduct.getpImages().isEmpty()) {
                String[] imagesToDelete = existingProduct.getpImages().toArray(new String[0]);
                cloudinaryService.deleteImages(imagesToDelete);
            }

            FirebaseDatabase.getInstance().getReference("products").child(pId).removeValueAsync();
            return ResponseEntity.ok(Map.of("message", "Product deleted successfully", "deletedId", pId));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    // --- UPDATE PRODUCT ---
    @PreAuthorize("hasRole('SELLER')")
    @PutMapping(value = "/update/{pId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Update a product",
            description = "Updates an existing product (Seller self-view only)",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponse(
            responseCode = "200",
            description = "Product updated successfully",
            content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = SellerProductResponseDto.class))
    )
    public ResponseEntity<?> updateProduct(
            @PathVariable String pId,
            @RequestParam("product") String productJson,
            @RequestParam(value = "images", required = false) List<MultipartFile> files
    ) {
        try {
            // 1. Fetch existing product
            Product existingProduct = getProductSync(pId);
            if (existingProduct == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Product not found"));
            }

            // 2. Ownership check
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (!existingProduct.getSellerEmail().equals(auth.getName())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "You do not own this product"));
            }

            // 3. Parse incoming JSON
            Gson gson = new Gson();
            Product updateData = gson.fromJson(productJson, Product.class);

            // 4. IMAGE HANDLING logic
            List<String> keptImages = updateData.getpImages() != null
                    ? new ArrayList<>(updateData.getpImages())
                    : new ArrayList<>();

            List<String> oldImages = existingProduct.getpImages() != null
                    ? existingProduct.getpImages()
                    : new ArrayList<>();

            // Determine deleted images (Present in old but missing in new list)
            List<String> imagesToDelete = oldImages.stream()
                    .filter(img -> !keptImages.contains(img))
                    .toList();

            if (!imagesToDelete.isEmpty()) {
                cloudinaryService.deleteImages(imagesToDelete.toArray(new String[0]));
            }

            // Upload new files
            if (files != null && !files.isEmpty()) {
                for (MultipartFile file : files) {
                    if (!file.isEmpty()) {
                        String newUrl = cloudinaryService.uploadProductImage(file);
                        keptImages.add(newUrl);
                    }
                }
            }

            // 5. MERGE FIELDS
            existingProduct.setpName(updateData.getpName());
            existingProduct.setpDescription(updateData.getpDescription());
            existingProduct.setpBrandName(updateData.getpBrandName());
            existingProduct.setpMrp(updateData.getpMrp());
            existingProduct.setpSellingPrice(updateData.getpSellingPrice());
            existingProduct.setpPurchasingPrice(updateData.getpPurchasingPrice());
            existingProduct.setpCreditScore(updateData.getpCreditScore());
            existingProduct.setCategory(updateData.getCategory());
            existingProduct.setSubCategory(updateData.getSubCategory());
            existingProduct.setKeywords(
                    updateData.getKeywords() != null ? updateData.getKeywords() : new ArrayList<>()
            );
            existingProduct.setConfidence(updateData.getConfidence());
            existingProduct.setMode(updateData.getMode());
            existingProduct.setpImages(keptImages); // Set updated list

            // 6. Save merged product
            FirebaseDatabase.getInstance()
                    .getReference("products")
                    .child(pId)
                    .setValueAsync(existingProduct);

            return ResponseEntity.ok(
                    Map.of(
                            "message", "Product updated successfully",
                            "product", mapToSellerDto(existingProduct)
                    )
            );

        } catch (Exception e) {
            log.error("Update failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    // --- HELPER METHODS ---

    private SellerProductResponseDto mapToSellerDto(Product p) {
        return SellerProductResponseDto.builder()
                .pId(p.getpId())
                .pName(p.getpName())
                .pDescription(p.getpDescription())
                .pBrandName(p.getpBrandName())
                .pMrp(p.getpMrp())
                .pSellingPrice(p.getpSellingPrice())
                .pPurchasingPrice(p.getpPurchasingPrice())
                .pCreditScore(p.getpCreditScore())
                .sellerEmail(p.getSellerEmail())
                .pImages(p.getpImages() != null ? p.getpImages() : new ArrayList<>())
                .category(p.getCategory())
                .subCategory(p.getSubCategory())
                .keywords(p.getKeywords())
                .confidence(p.getConfidence())
                .mode(p.getMode())
                .build();
    }

    private String generateProductId(String name, String brand) {
        String namePart = (name != null ? name : "XXXX").replaceAll("\\s+", "").toUpperCase();
        if (namePart.length() > 4) namePart = namePart.substring(0, 4);
        else while (namePart.length() < 4) namePart += "X";

        String brandPart = (brand != null ? brand : "XXXX").replaceAll("\\s+", "").toUpperCase();
        if (brandPart.length() > 4) brandPart = brandPart.substring(0, 4);
        else while (brandPart.length() < 4) brandPart += "X";

        int randomNum = new Random().nextInt(900) + 100;

        return namePart + brandPart + randomNum;
    }

    private Product getProductSync(String pId) throws ExecutionException, InterruptedException {
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("products").child(pId);
        CompletableFuture<Product> future = new CompletableFuture<>();

        ref.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                Product product = snapshot.getValue(Product.class);
                future.complete(product);
            }
            @Override
            public void onCancelled(DatabaseError error) {
                future.completeExceptionally(new RuntimeException(error.getMessage()));
            }
        });
        return future.get();
    }
}