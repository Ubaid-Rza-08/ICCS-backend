package com.ubaid.Auth.controller;

import com.google.firebase.database.*;
import com.ubaid.Auth.dto.SellerProductResponseDto;
import com.ubaid.Auth.model.Product;
import com.ubaid.Auth.service.CloudinaryService;
import com.google.gson.Gson;
import io.swagger.v3.oas.annotations.Operation;
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

    // --- 1. SEARCH PRODUCTS (Optimized & Secured) ---
    @GetMapping("/search")
    @PreAuthorize("hasRole('SELLER')") // FIX: Secure this endpoint
    @Operation(summary = "Search products by Name", description = "Efficiently searches global products starting with the query string")
    public CompletableFuture<ResponseEntity<List<Product>>> searchProducts(@RequestParam("name") String searchName) {
        log.info("Searching for products starting with: {}", searchName);

        CompletableFuture<ResponseEntity<List<Product>>> future = new CompletableFuture<>();
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("products");

        // OPTIMIZATION: Query Firebase directly instead of downloading everything
        // Note: This matches products that START with the search term (case-sensitive in Firebase)
        // To make it fully case-insensitive, you usually store a lowercase_name field in DB.
        ref.orderByChild("pName")
                .startAt(searchName)
                .endAt(searchName + "\uf8ff")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        List<Product> matches = new ArrayList<>();
                        for (DataSnapshot data : snapshot.getChildren()) {
                            Product p = data.getValue(Product.class);
                            if (p != null) matches.add(p);
                        }
                        // If no matches found with direct query, fall back to downloading (optional)
                        // or just return what we found.
                        log.info("Found {} matches", matches.size());
                        future.complete(ResponseEntity.ok(matches));
                    }

                    @Override
                    public void onCancelled(DatabaseError error) {
                        log.error("Search error: {}", error.getMessage());
                        future.complete(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                    }
                });
        return future;
    }

    // --- 2. CLONE PRODUCT ---
    @PreAuthorize("hasRole('SELLER')")
    @PostMapping("/add-existing/{existingProductId}")
    public ResponseEntity<?> addExistingProduct(
            @PathVariable String existingProductId,
            @RequestBody(required = false) Map<String, Object> overrides
    ) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String currentSellerEmail = auth.getName();

            // 1. Fetch Original
            Product originalProduct = getProductSync(existingProductId);
            if (originalProduct == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Product not found"));

            // 2. DUPLICATE CHECK: Prevent SAME seller from adding it twice
            if (checkProductExistsForSeller(currentSellerEmail, originalProduct.getpName())) {
                return ResponseEntity.badRequest().body(Map.of("error", "You are already selling this product!"));
            }

            // 3. Clone Logic
            Product newProduct = new Product();
            newProduct.setpName(originalProduct.getpName());
            newProduct.setpDescription(originalProduct.getpDescription());
            newProduct.setpBrandName(originalProduct.getpBrandName());
            newProduct.setCategory(originalProduct.getCategory());
            newProduct.setSubCategory(originalProduct.getSubCategory());
            newProduct.setpImages(originalProduct.getpImages());
            newProduct.setKeywords(originalProduct.getKeywords());
            newProduct.setSellerEmail(currentSellerEmail);
            newProduct.setpCreditScore(0);

            // Handle Prices
            int sellingPrice = originalProduct.getpSellingPrice();
            int mrp = originalProduct.getpMrp();
            if (overrides != null) {
                if (overrides.containsKey("pSellingPrice")) sellingPrice = (int) Double.parseDouble(overrides.get("pSellingPrice").toString());
                if (overrides.containsKey("pMrp")) mrp = (int) Double.parseDouble(overrides.get("pMrp").toString());
            }
            newProduct.setpSellingPrice(sellingPrice);
            newProduct.setpMrp(mrp);

            // Save
            String newCustomId = generateProductId(newProduct.getpName(), newProduct.getpBrandName());
            newProduct.setpId(newCustomId);
            FirebaseDatabase.getInstance().getReference("products").child(newCustomId).setValueAsync(newProduct);

            return ResponseEntity.ok(Map.of("message", "Product added", "productId", newCustomId));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    // --- 3. CREATE NEW PRODUCT ---
    @PreAuthorize("hasRole('SELLER')")
    @PostMapping(value = "/create-product", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> createProduct(
            @RequestParam("product") String productJson,
            @RequestParam(value = "images", required = false) List<MultipartFile> files
    ) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String currentSellerEmail = auth.getName();
            Gson gson = new Gson();
            Product product = gson.fromJson(productJson, Product.class);

            // DUPLICATE CHECK FOR MANUAL CREATION
            if (checkProductExistsForSeller(currentSellerEmail, product.getpName())) {
                return ResponseEntity.badRequest().body(Map.of("error", "You are already selling a product with this name!"));
            }

            product.setSellerEmail(currentSellerEmail);

            // Image Upload Logic...
            List<String> finalImages = new ArrayList<>();
            if (product.getpImages() != null) finalImages.addAll(product.getpImages());
            if (files != null && !files.isEmpty()) {
                for (MultipartFile file : files) {
                    if (cloudinaryService.isValidFileSize(file, 5.0)) {
                        finalImages.add(cloudinaryService.uploadProductImage(file));
                    }
                }
            }
            product.setpImages(finalImages);
            if (product.getKeywords() == null) product.setKeywords(new ArrayList<>());

            String customId = generateProductId(product.getpName(), product.getpBrandName());
            product.setpId(customId);

            FirebaseDatabase.getInstance().getReference("products").child(customId).setValueAsync(product);
            return ResponseEntity.ok(Map.of("message", "Product created", "productId", customId));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    // --- Helpers (No changes needed, kept for completeness) ---
    // (Ensure you keep your existing updateProduct, deleteProduct, getAllProducts, checkProductExistsForSeller, etc.)
    // ... [Paste your existing helper methods here]

    private boolean checkProductExistsForSeller(String sellerEmail, String productName) throws ExecutionException, InterruptedException {
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("products");
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        ref.orderByChild("sellerEmail").equalTo(sellerEmail)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        boolean found = false;
                        for (DataSnapshot data : snapshot.getChildren()) {
                            Product p = data.getValue(Product.class);
                            if (p != null && p.getpName() != null && p.getpName().equalsIgnoreCase(productName)) {
                                found = true;
                                break;
                            }
                        }
                        future.complete(found);
                    }
                    @Override
                    public void onCancelled(DatabaseError error) { future.complete(false); }
                });
        return future.get();
    }

    private String generateProductId(String name, String brand) {
        String namePart = (name != null ? name : "XXXX").replaceAll("\\s+", "").toUpperCase();
        if (namePart.length() > 4) namePart = namePart.substring(0, 4);
        else while (namePart.length() < 4) namePart += "X";
        String brandPart = (brand != null ? brand : "XXXX").replaceAll("\\s+", "").toUpperCase();
        if (brandPart.length() > 4) brandPart = brandPart.substring(0, 4);
        else while (brandPart.length() < 4) brandPart += "X";
        return namePart + brandPart + (new Random().nextInt(900) + 100);
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