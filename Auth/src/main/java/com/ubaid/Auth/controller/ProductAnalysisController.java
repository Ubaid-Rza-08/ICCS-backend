package com.ubaid.Auth.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ubaid.Auth.dto.ProductAnalysisResponse;
import com.ubaid.Auth.service.GeminiService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api")
@Slf4j
public class ProductAnalysisController {

    private final GeminiService geminiService;
    private final ObjectMapper objectMapper;

    public ProductAnalysisController(GeminiService geminiService, ObjectMapper objectMapper) {
        this.geminiService = geminiService;
        this.objectMapper = objectMapper;
    }

    private static final String PRODUCT_ANALYSIS_PROMPT = """
            Analyze the product shown in the image strictly.
            
            You must return a raw JSON object only. Do not wrap it in markdown formatted code blocks (no ```json ... ```).
            
            Fields to extract:
            - title: A short, descriptive title of the product.
            - brand: The brand name if visible, else 'Generic'.
            - category: The main category (e.g., Electronics, Fashion).
            - sub_category: A more specific category.
            - description: A short description (max 2 sentences).
            - keywords: A list of 5 search keywords.
            - confidence: A number between 0.0 and 1.0 indicating how clear the image is.
            - mode: Always return "AI_DETECTED".
            
            JSON Structure:
            {
              "product_data": {
                "title": "string",
                "brand": "string",
                "category": "string",
                "sub_category": "string",
                "description": "string",
                "keywords": ["string"],
                "confidence": 0.95,
                "mode": "AI_DETECTED"
              }
            }
            """;

    @PostMapping(
            value = "/analyze-product",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<?> analyzeProduct(@RequestParam("image") MultipartFile image) {

        log.info("Received request to analyze product image: {}",
                image != null ? image.getOriginalFilename() : "null");

        if (image == null || image.isEmpty()) {
            log.warn("Analysis failed: Image file is missing or empty");
            return ResponseEntity.badRequest().body(Map.of("error", "Image file is required"));
        }

        try {
            log.debug("Sending image to Gemini Service for analysis...");
            // 1. Pass bytes and content type (e.g., image/png) to service
            String rawResponse = geminiService.analyzeImage(
                    PRODUCT_ANALYSIS_PROMPT,
                    image.getBytes(),
                    image.getContentType()
            );

            log.debug("Raw AI Response received");

            // 2. Extract AI text
            JsonNode root = objectMapper.readTree(rawResponse);
            JsonNode candidates = root.path("candidates");

            if (candidates.isEmpty()) {
                log.error("AI returned no candidates for image: {}", image.getOriginalFilename());
                return ResponseEntity.status(502).body(Map.of("error", "AI returned no candidates. The image might be unsafe or unclear."));
            }

            JsonNode textNode = candidates.path(0)
                    .path("content")
                    .path("parts")
                    .path(0)
                    .path("text");

            if (textNode.isMissingNode()) {
                log.error("Invalid AI response structure");
                return ResponseEntity.internalServerError().body(Map.of("error", "Invalid AI response structure"));
            }

            // 3. Clean Gemini noise (Markdown stripping)
            String cleanJson = textNode.asText()
                    .replaceAll("```json", "")
                    .replaceAll("```", "")
                    .trim();

            // 4. Deserialize
            ProductAnalysisResponse response = objectMapper.readValue(cleanJson, ProductAnalysisResponse.class);

            log.info("Product analysis completed successfully for: {}", response.getProductData().getTitle());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Product analysis failed with exception", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of(
                            "error", "Product analysis failed",
                            "details", e.getMessage()
                    ));
        }
    }
}