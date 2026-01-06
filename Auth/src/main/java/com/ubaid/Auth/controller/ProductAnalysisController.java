package com.ubaid.Auth.controller;

import com.ubaid.Auth.dto.ProductAnalysisResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
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
    @Operation(summary = "Analyze product image", description = "Uploads an image to get AI-generated product details (Dummy Data)", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(
            responseCode = "200",
            description = "Analysis successful",
            content = @Content(schema = @Schema(implementation = ProductAnalysisResponse.class))
    )
    public ResponseEntity<?> analyzeProduct(
            @Parameter(description = "Image file to analyze")
            @RequestParam("image") MultipartFile file
    ) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Image file is required"));
        }

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

        return ResponseEntity.ok(new ProductAnalysisResponse(dummyData));
    }
}