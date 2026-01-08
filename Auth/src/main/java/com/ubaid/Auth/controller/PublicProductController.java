package com.ubaid.Auth.controller;

import com.google.firebase.database.*;
import com.ubaid.Auth.dto.PublicProductResponseDto;
import com.ubaid.Auth.model.Product;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
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
@RequestMapping("/api/public/products")
@RequiredArgsConstructor
public class PublicProductController {

    // --- 1. GET ALL PRODUCTS ---
    @GetMapping
    public CompletableFuture<ResponseEntity<?>> getAllPublicProducts() {
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("products");
        CompletableFuture<ResponseEntity<?>> future = new CompletableFuture<>();

        ref.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                List<PublicProductResponseDto> productList = new ArrayList<>();
                for (DataSnapshot data : snapshot.getChildren()) {
                    Product product = data.getValue(Product.class);
                    if (product != null) {
                        productList.add(mapToDto(product));
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

    // --- 2. GET PRODUCT BY ID ---
    @GetMapping("/{pId}")
    @Operation(summary = "Get a single product by ID")
    @ApiResponse(responseCode = "200", content = @Content(mediaType = "application/json", schema = @Schema(implementation = PublicProductResponseDto.class)))
    @ApiResponse(responseCode = "404", description = "Product not found")
    public CompletableFuture<ResponseEntity<?>> getProductById(@PathVariable String pId) {
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("products").child(pId);
        CompletableFuture<ResponseEntity<?>> future = new CompletableFuture<>();

        ref.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                Product product = snapshot.getValue(Product.class);
                if (product != null) {
                    future.complete(ResponseEntity.ok(mapToDto(product)));
                } else {
                    future.complete(ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .body(Map.of("error", "Product not found with ID: " + pId)));
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
                future.complete(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("error", "Error fetching product: " + error.getMessage())));
            }
        });

        return future;
    }

    // --- 3. SEARCH BY NAME ---
    @GetMapping("/search")
    public CompletableFuture<ResponseEntity<?>> searchProductByName(@RequestParam("name") String queryName) {
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("products");
        CompletableFuture<ResponseEntity<?>> future = new CompletableFuture<>();

        Query query = ref.orderByChild("pName")
                .startAt(queryName)
                .endAt(queryName + "\uf8ff");

        query.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                List<PublicProductResponseDto> searchResults = new ArrayList<>();
                for (DataSnapshot data : snapshot.getChildren()) {
                    Product product = data.getValue(Product.class);
                    if (product != null) {
                        searchResults.add(mapToDto(product));
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

    // --- 4. SEARCH BY DESCRIPTION ---
    @GetMapping("/search-description")
    @Operation(summary = "Search products by description")
    @ApiResponse(responseCode = "200", content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = PublicProductResponseDto.class))))
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

    // --- 5. SEARCH BY KEYWORDS ---
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
                        boolean matchFound = product.getKeywords().stream()
                                .map(String::toLowerCase)
                                .anyMatch(prodKeyword -> normalizedKeywords.stream()
                                        .anyMatch(prodKeyword::contains));

                        if (matchFound) {
                            matchingProducts.add(mapToDto(product));
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

    // --- HELPER METHOD ---
    private PublicProductResponseDto mapToDto(Product product) {
        return PublicProductResponseDto.builder()
                .pId(product.getpId())
                .pName(product.getpName())
                .pDescription(product.getpDescription())
                .pBrandName(product.getpBrandName())
                // FIXED LINE BELOW: Handles null images safely
                .pImages(product.getpImages() != null ? product.getpImages() : new ArrayList<>())
                .pSellingPrice(product.getpSellingPrice())
                .pMrp(product.getpMrp())
                .category(product.getCategory())
                .subCategory(product.getSubCategory())
                .keywords(product.getKeywords())
                .build();
    }
}