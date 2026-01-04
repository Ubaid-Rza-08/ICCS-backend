package com.ubaid.Auth.controller;

import com.google.firebase.database.*;
import com.google.gson.Gson;
import com.ubaid.Auth.model.Product;
import com.ubaid.Auth.service.CloudinaryService;
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

            DatabaseReference ref = FirebaseDatabase.getInstance().getReference("products");
            String uniqueId = ref.push().getKey();
            product.setpId(uniqueId);
            ref.child(uniqueId).setValueAsync(product);

            response.put("message", "Product created successfully");
            response.put("productId", uniqueId);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error creating product: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    // --- GET ALL PRODUCTS (For Current Seller) ---
    @PreAuthorize("hasRole('SELLER')")
    @GetMapping("/all")
    public CompletableFuture<ResponseEntity<?>> getAllProducts() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String currentSellerEmail = auth.getName();

        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("products");
        CompletableFuture<ResponseEntity<?>> future = new CompletableFuture<>();

        // Query products where 'sellerEmail' matches the logged-in user
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
    public ResponseEntity<?> deleteProduct(@PathVariable String pId) {
        try {
            // 1. Fetch existing product synchronously to verify owner and get images
            Product existingProduct = getProductSync(pId);

            if (existingProduct == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Product not found"));
            }

            // 2. Verify Ownership
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (!existingProduct.getSellerEmail().equals(auth.getName())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "You do not own this product"));
            }

            // 3. Delete images from Cloudinary
            if (existingProduct.getpImages() != null && !existingProduct.getpImages().isEmpty()) {
                // Convert List to Array for your varargs method
                String[] imagesToDelete = existingProduct.getpImages().toArray(new String[0]);
                cloudinaryService.deleteImages(imagesToDelete);
            }

            // 4. Delete from Firebase
            FirebaseDatabase.getInstance().getReference("products").child(pId).removeValueAsync();

            return ResponseEntity.ok(Map.of("message", "Product deleted successfully", "deletedId", pId));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    // --- UPDATE PRODUCT ---
    @PreAuthorize("hasRole('SELLER')")
    @PutMapping(value = "/update/{pId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> updateProduct(
            @PathVariable String pId,
            @RequestParam("product") String productJson,
            @RequestParam(value = "images", required = false) List<MultipartFile> files
    ) {
        try {
            // 1. Fetch Existing Product
            Product existingProduct = getProductSync(pId);
            if (existingProduct == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Product not found"));
            }

            // 2. Verify Ownership
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (!existingProduct.getSellerEmail().equals(auth.getName())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "You do not own this product"));
            }

            // 3. Parse Update Data
            Gson gson = new Gson();
            Product updateData = gson.fromJson(productJson, Product.class);

            // 4. Handle Image Logic
            // Start with images the user sent in JSON (images they want to KEEP)
            List<String> finalImages = updateData.getpImages() != null ? updateData.getpImages() : new ArrayList<>();
            List<String> oldImages = existingProduct.getpImages() != null ? existingProduct.getpImages() : new ArrayList<>();

            // Detect removed images (In DB but NOT in new JSON list) -> Delete them from Cloudinary
            List<String> imagesToDelete = oldImages.stream()
                    .filter(img -> !finalImages.contains(img))
                    .collect(Collectors.toList());

            if (!imagesToDelete.isEmpty()) {
                cloudinaryService.deleteImages(imagesToDelete.toArray(new String[0]));
                log.info("Deleted {} removed images", imagesToDelete.size());
            }

            // 5. Upload New Images (if any)
            if (files != null && !files.isEmpty()) {
                for (MultipartFile file : files) {
                    if (!file.isEmpty()) {
                        String newUrl = cloudinaryService.uploadProductImage(file);
                        finalImages.add(newUrl);
                    }
                }
            }
            updateData.setpImages(finalImages);

            // 6. Preserve Immutable Fields
            updateData.setpId(pId);
            updateData.setSellerEmail(existingProduct.getSellerEmail()); // Ensure ownership doesn't change

            // 7. Save Update to Firebase
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

    // --- HELPER: Synchronous Firebase Read ---
    private Product getProductSync(String pId) throws ExecutionException, InterruptedException {
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("products").child(pId);
        CompletableFuture<Product> future = new CompletableFuture<>();

        ref.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                Product product = snapshot.getValue(Product.class);
                future.complete(product); // Returns null if not found
            }
            @Override
            public void onCancelled(DatabaseError error) {
                future.completeExceptionally(new RuntimeException(error.getMessage()));
            }
        });

        return future.get(); // Blocks until data is retrieved
    }
}