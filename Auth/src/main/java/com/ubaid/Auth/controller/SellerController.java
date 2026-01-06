package com.ubaid.Auth.controller;

import com.google.firebase.database.*;
import com.google.gson.Gson;
import com.ubaid.Auth.dto.PublicProductResponseDto;
import com.ubaid.Auth.model.Product;
import com.ubaid.Auth.service.CloudinaryService;
import io.swagger.v3.oas.annotations.Operation;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

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
            Gson gson = new Gson();
            Product product = gson.fromJson(productJson, Product.class);

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String currentSellerEmail = auth.getName();
            product.setSellerEmail(currentSellerEmail);

            List<String> uploadedImageUrls = new ArrayList<>();
            if (files != null && !files.isEmpty()) {
                for (MultipartFile file : files) {
                    if (file.isEmpty()) continue;
                    if (!cloudinaryService.isValidFileSize(file, 5.0)) {
                        return ResponseEntity.badRequest().body(Map.of("error", "File exceeds 5MB limit"));
                    }
                    String url = cloudinaryService.uploadProductImage(file);
                    uploadedImageUrls.add(url);
                }
            }
            product.setpImages(uploadedImageUrls);

            if (product.getKeywords() == null) product.setKeywords(new ArrayList<>());

            String customId = generateProductId(product.getpName(), product.getpBrandName());
            product.setpId(customId);

            DatabaseReference ref = FirebaseDatabase.getInstance().getReference("products");
            ref.child(customId).setValueAsync(product);

            response.put("message", "Product created successfully");
            response.put("productId", customId);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error creating product: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    // --- GET ALL PRODUCTS ---
    @PreAuthorize("hasRole('SELLER')")
    @GetMapping("/all")
    @Operation(summary = "Get all seller products", description = "Retrieves all products belonging to the logged-in seller", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(
            responseCode = "200",
            description = "List of products",
            content = @Content(
                    mediaType = "application/json",
                    array = @ArraySchema(schema = @Schema(implementation = Product.class))
            )
    )
    public CompletableFuture<ResponseEntity<?>> getAllProducts() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String currentSellerEmail = auth.getName();

        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("products");
        CompletableFuture<ResponseEntity<?>> future = new CompletableFuture<>();

        Query query = ref.orderByChild("sellerEmail").equalTo(currentSellerEmail);

        query.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                List<Product> productList = new ArrayList<>();
                for (DataSnapshot data : snapshot.getChildren()) {
                    Product product = data.getValue(Product.class);
                    if (product != null) {
                        productList.add(product);
                    }
                }
                future.complete(ResponseEntity.ok(productList));
            }
            @Override
            public void onCancelled(DatabaseError error) {
                future.complete(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("error", error.getMessage())));
            }
        });
        return future;
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
    @Operation(summary = "Update a product", description = "Updates an existing product (Owner only)", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(
            responseCode = "200",
            description = "Product updated successfully",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = Product.class))
    )
    public ResponseEntity<?> updateProduct(
            @PathVariable String pId,
            @RequestParam("product") String productJson,
            @RequestParam(value = "images", required = false) List<MultipartFile> files
    ) {
        try {
            Product existingProduct = getProductSync(pId);
            if (existingProduct == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Product not found"));

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (!existingProduct.getSellerEmail().equals(auth.getName())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "You do not own this product"));
            }

            Gson gson = new Gson();
            Product updateData = gson.fromJson(productJson, Product.class);

            List<String> finalImages = updateData.getpImages() != null ? updateData.getpImages() : new ArrayList<>();
            List<String> oldImages = existingProduct.getpImages() != null ? existingProduct.getpImages() : new ArrayList<>();

            List<String> imagesToDelete = oldImages.stream()
                    .filter(img -> !finalImages.contains(img))
                    .collect(Collectors.toList());

            if (!imagesToDelete.isEmpty()) {
                cloudinaryService.deleteImages(imagesToDelete.toArray(new String[0]));
            }

            if (files != null && !files.isEmpty()) {
                for (MultipartFile file : files) {
                    if (!file.isEmpty()) {
                        String newUrl = cloudinaryService.uploadProductImage(file);
                        finalImages.add(newUrl);
                    }
                }
            }
            updateData.setpImages(finalImages);
            updateData.setpId(pId);
            updateData.setSellerEmail(existingProduct.getSellerEmail());

            if (updateData.getKeywords() == null) updateData.setKeywords(new ArrayList<>());

            FirebaseDatabase.getInstance().getReference("products").child(pId).setValueAsync(updateData);

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Product updated successfully");
            response.put("product", updateData);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Update failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    // --- SEARCH BY DESCRIPTION ---
    @GetMapping("/search-description")
    @Operation(summary = "Search products by description")
    @ApiResponse(
            responseCode = "200",
            description = "Successful operation",
            content = @Content(
                    mediaType = "application/json",
                    array = @ArraySchema(schema = @Schema(implementation = PublicProductResponseDto.class))
            )
    )
    public CompletableFuture<ResponseEntity<?>> searchProductsByDescription(@RequestParam("query") String query) {
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("products");
        CompletableFuture<ResponseEntity<?>> future = new CompletableFuture<>();

        String normalizedQuery = query.toLowerCase().trim();

        ref.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                List<PublicProductResponseDto> matchingProducts = new ArrayList<>();

                for (DataSnapshot data : snapshot.getChildren()) {
                    Product product = data.getValue(Product.class);

                    if (product != null &&
                            product.getpDescription() != null &&
                            product.getpDescription().toLowerCase().contains(normalizedQuery)) {

                        matchingProducts.add(mapToDto(product));
                    }
                }
                future.complete(ResponseEntity.ok(matchingProducts));
            }

            @Override
            public void onCancelled(DatabaseError error) {
                future.complete(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("error", error.getMessage())));
            }
        });

        return future;
    }
    @GetMapping("/search-keywords")
    public CompletableFuture<ResponseEntity<?>> searchProductsByKeywords(@RequestParam("keywords") List<String> searchKeywords) {
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("products");
        CompletableFuture<ResponseEntity<?>> future = new CompletableFuture<>();

        List<String> normalizedKeywords = searchKeywords.stream()
                .map(String::toLowerCase)
                .map(String::trim)
                .collect(Collectors.toList());

        ref.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                List<PublicProductResponseDto> matchingProducts = new ArrayList<>();

                for (DataSnapshot data : snapshot.getChildren()) {
                    Product product = data.getValue(Product.class);
                    if (product != null && product.getKeywords() != null) {

                        // Check for match
                        boolean matchFound = product.getKeywords().stream()
                                .map(String::toLowerCase)
                                .anyMatch(prodKeyword -> normalizedKeywords.stream()
                                        .anyMatch(prodKeyword::contains));

                        if (matchFound) {
                            // MAP TO DTO (Excluding ID, Seller, and Prices)
                            PublicProductResponseDto dto = PublicProductResponseDto.builder()
                                    .pName(product.getpName())
                                    .pDescription(product.getpDescription())
                                    .pBrandName(product.getpBrandName())
                                    .pCreditScore(product.getpCreditScore())
                                    .pImages(product.getpImages())
                                    .category(product.getCategory())
                                    .subCategory(product.getSubCategory())
                                    .keywords(product.getKeywords())
                                    .confidence(product.getConfidence())
                                    .mode(product.getMode())
                                    .build();

                            matchingProducts.add(dto);
                        }
                    }
                }
                future.complete(ResponseEntity.ok(matchingProducts));
            }

            @Override
            public void onCancelled(DatabaseError error) {
                future.complete(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("error", "Search error: " + error.getMessage())));
            }
        });

        return future;
    }

    // --- HELPER METHODS ---

    private PublicProductResponseDto mapToDto(Product product) {
        return PublicProductResponseDto.builder()
                .pName(product.getpName())
                .pDescription(product.getpDescription())
                .pBrandName(product.getpBrandName())
                .pCreditScore(product.getpCreditScore())
                .pImages(product.getpImages())
                .category(product.getCategory())
                .subCategory(product.getSubCategory())
                .keywords(product.getKeywords())
                .confidence(product.getConfidence())
                .mode(product.getMode())
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