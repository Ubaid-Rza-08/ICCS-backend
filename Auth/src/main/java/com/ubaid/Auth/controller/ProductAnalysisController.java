package com.ubaid.Auth.controller;

import com.ubaid.Auth.dto.ProductAnalysisResponse; // Import the DTO
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ProductAnalysisController {

    @PostMapping(value = "/analyze-product", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> analyzeProduct(@RequestParam("image") MultipartFile file) {

        // 1. Basic Validation
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Image file is required"));
        }

        // 2. Generate Dummy Data
        ProductAnalysisResponse.ProductData dummyData = ProductAnalysisResponse.ProductData.builder()
                .title("Nike Air Zoom Pegasus 39")
                .category("Footwear")
                .subCategory("Running Shoes")
                .brand("Nike")
                .keywords(Arrays.asList("running", "sports", "sneakers", "breathable", "cushioned"))
                .description("The Nike Air Zoom Pegasus 39 features a supportive fit and responsive cushioning to help you reach your goals.")
                .confidence(0.95)
                .mode("HIGH_CONFIDENCE")
                .build();

        // 3. Return Response
        return ResponseEntity.ok(new ProductAnalysisResponse(dummyData));
    }
}