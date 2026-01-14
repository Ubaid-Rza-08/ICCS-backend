package com.ubaid.Auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PublicProductResponseDto {

    // Basic Details
    private String pId;
    private String pName;
    private String pDescription;
    private String pBrandName;
    private List<String> pImages;

    // Pricing
    private double pSellingPrice;
    private double pMrp;

    // Categorization & SEO
    private String category;
    private String subCategory;
    private List<String> keywords;

    // Sorting & Ranking (Added)
    private double pCreditScore;
}