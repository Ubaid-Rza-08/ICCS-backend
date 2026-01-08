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
public class ProductRequestDto {

    private String pName;
    private String pDescription;
    private String pBrandName;

    // Added field for handling existing images during updates
    private List<String> pImages;

    // Prices changed to int as requested
    private int pMrp;
    private int pSellingPrice;
    private int pPurchasingPrice;

    private int pCreditScore;

    private String sellerEmail;

    private String category;
    private String subCategory;

    private List<String> keywords;

    @Builder.Default
    private Double confidence = 0.99;

    @Builder.Default
    private String mode = "HIGH_CONFIDENCE";
}