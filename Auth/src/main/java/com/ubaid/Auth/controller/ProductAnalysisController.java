package com.ubaid.Auth.controller;

import com.ubaid.Auth.dto.ProductAnalysisResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class ProductAnalysisController {

    // Initialize Logger
    private static final Logger logger = LoggerFactory.getLogger(ProductAnalysisController.class);

    @Value("${ai.service.url}")
    private String aiServiceBaseUrl;

    private final RestTemplate restTemplate;

    public ProductAnalysisController() {
        this.restTemplate = new RestTemplate();
    }

    @PostMapping(value = "/analyze-product", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Analyze product image", description = "Uploads an image to get AI-generated product details", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(
            responseCode = "200",
            description = "Analysis successful",
            content = @Content(schema = @Schema(implementation = ProductAnalysisResponse.class))
    )
    public ResponseEntity<?> analyzeProduct(
            @Parameter(description = "Image file to analyze")
            @RequestParam("image") MultipartFile file
    ) {
        // LOG: Entry point
        logger.info("Request received to analyze product image: {}", (file != null ? file.getOriginalFilename() : "null"));

        if (file == null || file.isEmpty()) {
            logger.warn("Analysis failed: Image file is missing or empty.");
            return ResponseEntity.badRequest().body(Map.of("error", "Image file is required"));
        }

        try {
            // 1. Prepare Headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            // 2. Prepare Body
            ByteArrayResource fileResource = new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename();
                }
            };

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", fileResource);

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            // 3. Construct the Full URL
            String fullUrl = aiServiceBaseUrl + "/analyze-product";
            logger.info("Forwarding request to AI Service at: {}", fullUrl);

            // 4. Call the External API
            ResponseEntity<ProductAnalysisResponse> response = restTemplate.postForEntity(
                    fullUrl,
                    requestEntity,
                    ProductAnalysisResponse.class
            );

            // LOG: Success
            logger.info("AI Service analysis completed successfully for: {}", file.getOriginalFilename());

            // 5. Return the real data
            return ResponseEntity.ok(response.getBody());

        } catch (Exception e) {
            // LOG: Error with stack trace
            logger.error("Error occurred while analyzing product image: {}", file.getOriginalFilename(), e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to analyze product: " + e.getMessage()));
        }
    }
}