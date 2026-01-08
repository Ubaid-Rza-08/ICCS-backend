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
public class SellerProductResponseDto {

    private String pId;
    private String pName;
    private String pDescription;
    private String pBrandName;

    private int pMrp;
    private int pSellingPrice;
    private int pPurchasingPrice;

    private int pCreditScore;
    private String sellerEmail;

    private List<String> pImages;

    private String category;
    private String subCategory;
    private List<String> keywords;

    private double confidence;
    private String mode;
}

