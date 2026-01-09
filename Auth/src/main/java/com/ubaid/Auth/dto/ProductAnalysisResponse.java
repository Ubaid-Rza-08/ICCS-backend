package com.ubaid.Auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ProductAnalysisResponse {

    @JsonProperty("product_data")
    private ProductData productData;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ProductData {
        private String title;
        private String category;
        @JsonProperty("sub_category")
        private String subCategory;
        private String brand;
        private List<String> keywords;
        private String description;
        private double confidence;
        private String mode;
    }
}