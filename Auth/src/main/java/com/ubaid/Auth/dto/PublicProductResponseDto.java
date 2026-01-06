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
    // Excluded: pId, sellerEmail, pMrp, pPurchasingPrice, pSellingPrice

    // Included Fields
    private String pName;
    private String pDescription;
    private String pBrandName;
    private int pCreditScore;
    private List<String> pImages;

    // AI Analysis Fields
    private String category;
    private String subCategory;
    private List<String> keywords;
    private double confidence;
    private String mode;
}