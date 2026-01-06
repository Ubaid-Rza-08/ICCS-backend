package com.ubaid.Auth.controller;

import com.google.firebase.database.*;
import com.ubaid.Auth.dto.PublicProductResponseDto;
import com.ubaid.Auth.model.Product;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/public/products") // Public endpoint
@RequiredArgsConstructor
public class PublicProductController {

    // --- 1. GET ALL PRODUCTS (No Auth Required) ---
    @GetMapping
    public CompletableFuture<ResponseEntity<?>> getAllPublicProducts() {
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("products");
        CompletableFuture<ResponseEntity<?>> future = new CompletableFuture<>();

        ref.addListenerForSingleValueEvent(new ValueEventListener() {
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
                        .body("Error fetching data: " + error.getMessage()));
            }
        });

        return future;
    }

    // --- 2. SEARCH BY NAME (StartAt/EndAt logic) ---
    @GetMapping("/search")
    public CompletableFuture<ResponseEntity<?>> searchProductByName(@RequestParam("name") String queryName) {
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("products");
        CompletableFuture<ResponseEntity<?>> future = new CompletableFuture<>();

        // Perform "Starts With" search
        // Note: Firebase searches are Case Sensitive by default
        Query query = ref.orderByChild("pName")
                .startAt(queryName)
                .endAt(queryName + "\uf8ff");

        query.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                List<Product> searchResults = new ArrayList<>();
                for (DataSnapshot data : snapshot.getChildren()) {
                    Product product = data.getValue(Product.class);
                    if (product != null) {
                        searchResults.add(product);
                    }
                }
                future.complete(ResponseEntity.ok(searchResults));
            }

            @Override
            public void onCancelled(DatabaseError error) {
                future.complete(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("Search error: " + error.getMessage()));
            }
        });

        return future;
    }
//    @GetMapping("/search-keywords")
//    public CompletableFuture<ResponseEntity<?>> searchProductsByKeywords(@RequestParam("keywords") List<String> searchKeywords) {
//        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("products");
//        CompletableFuture<ResponseEntity<?>> future = new CompletableFuture<>();
//
//        List<String> normalizedKeywords = searchKeywords.stream()
//                .map(String::toLowerCase)
//                .map(String::trim)
//                .collect(Collectors.toList());
//
//        ref.addListenerForSingleValueEvent(new ValueEventListener() {
//            @Override
//            public void onDataChange(DataSnapshot snapshot) {
//                List<PublicProductResponseDto> matchingProducts = new ArrayList<>();
//
//                for (DataSnapshot data : snapshot.getChildren()) {
//                    Product product = data.getValue(Product.class);
//                    if (product != null && product.getKeywords() != null) {
//
//                        // Check for match
//                        boolean matchFound = product.getKeywords().stream()
//                                .map(String::toLowerCase)
//                                .anyMatch(prodKeyword -> normalizedKeywords.stream()
//                                        .anyMatch(prodKeyword::contains));
//
//                        if (matchFound) {
//                            // MAP TO DTO (Excluding ID, Seller, and Prices)
//                            PublicProductResponseDto dto = PublicProductResponseDto.builder()
//                                    .pName(product.getpName())
//                                    .pDescription(product.getpDescription())
//                                    .pBrandName(product.getpBrandName())
//                                    .pCreditScore(product.getpCreditScore())
//                                    .pImages(product.getpImages())
//                                    .category(product.getCategory())
//                                    .subCategory(product.getSubCategory())
//                                    .keywords(product.getKeywords())
//                                    .confidence(product.getConfidence())
//                                    .mode(product.getMode())
//                                    .build();
//
//                            matchingProducts.add(dto);
//                        }
//                    }
//                }
//                future.complete(ResponseEntity.ok(matchingProducts));
//            }
//
//            @Override
//            public void onCancelled(DatabaseError error) {
//                future.complete(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
//                        .body(Map.of("error", "Search error: " + error.getMessage())));
//            }
//        });
//
//        return future;
//    }
}