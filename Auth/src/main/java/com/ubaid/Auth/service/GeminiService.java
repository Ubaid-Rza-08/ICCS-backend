package com.ubaid.Auth.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class GeminiService {

    private final WebClient webClient;

    @Value("${gemini.api.key}")
    private String geminiApiKey;

    @Value("${gemini.api.url}")
    private String geminiApiUrl;

    public GeminiService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024)) // 10MB
                .build();
    }

    // MATCHING METHOD SIGNATURE FOR YOUR CONTROLLER
    public String analyzeImage(String prompt, byte[] imageBytes, String mimeType) {
        try {
            // 1. Sanitize config
            String safeKey = geminiApiKey.trim();
            String safeUrl = geminiApiUrl.trim();

            // 2. Encode Image
            String base64Image = Base64.getEncoder().encodeToString(imageBytes);

            // 3. Handle null content type (default to jpeg if null)
            String safeMimeType = (mimeType != null && !mimeType.isEmpty()) ? mimeType : "image/jpeg";

            // 4. Construct Payload
            Map<String, Object> requestBody = Map.of(
                    "contents", List.of(
                            Map.of("parts", List.of(
                                    Map.of("text", prompt),
                                    Map.of("inline_data", Map.of(
                                            "mime_type", safeMimeType,
                                            "data", base64Image
                                    ))
                            ))
                    )
            );

            // 5. Build URL
            String fullUrl = safeUrl.contains("?key=")
                    ? safeUrl
                    : String.format("%s?key=%s", safeUrl, safeKey);

            log.info("Sending request to Gemini with MimeType: {}", safeMimeType);

            // 6. Execute
            return webClient.post()
                    .uri(fullUrl)
                    .header("Content-Type", "application/json")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(60))
                    .block();

        } catch (WebClientResponseException e) {
            log.error("Gemini API Error: {} - Body: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("AI Service Error: " + e.getStatusCode());
        } catch (Exception e) {
            log.error("Internal AI Service Error", e);
            throw new RuntimeException("AI processing failed: " + e.getMessage());
        }
    }
}