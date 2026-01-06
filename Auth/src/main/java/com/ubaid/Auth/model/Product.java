package com.ubaid.Auth.model;

import com.google.firebase.database.PropertyName;
import java.util.ArrayList;
import java.util.List;

public class Product {

    // --- ID Field ---
    private String pId;

    @PropertyName("pId") // Matches key in DB screenshot
    public String getpId() { return pId; }
    @PropertyName("pId")
    public void setpId(String pId) { this.pId = pId; }

    // --- Name ---
    private String pName;

    @PropertyName("pName") // Matches key in DB screenshot
    public String getpName() { return pName; }
    @PropertyName("pName")
    public void setpName(String pName) { this.pName = pName; }

    // --- Selling Price ---
    private int pSellingPrice;

    @PropertyName("pSellingPrice") // Matches key in DB screenshot
    public int getpSellingPrice() { return pSellingPrice; }
    @PropertyName("pSellingPrice")
    public void setpSellingPrice(int pSellingPrice) { this.pSellingPrice = pSellingPrice; }

    // --- MRP ---
    private int pMrp;

    @PropertyName("pMrp")
    public int getpMrp() { return pMrp; }
    @PropertyName("pMrp")
    public void setpMrp(int pMrp) { this.pMrp = pMrp; }

    // --- Purchasing Price ---
    private int pPurchasingPrice;

    @PropertyName("pPurchasingPrice")
    public int getpPurchasingPrice() { return pPurchasingPrice; }
    @PropertyName("pPurchasingPrice")
    public void setpPurchasingPrice(int pPurchasingPrice) { this.pPurchasingPrice = pPurchasingPrice; }

    // --- Description ---
    private String pDescription;

    @PropertyName("pDescription")
    public String getpDescription() { return pDescription; }
    @PropertyName("pDescription")
    public void setpDescription(String pDescription) { this.pDescription = pDescription; }

    // --- Brand ---
    private String pBrandName;

    @PropertyName("pBrandName")
    public String getpBrandName() { return pBrandName; }
    @PropertyName("pBrandName")
    public void setpBrandName(String pBrandName) { this.pBrandName = pBrandName; }

    // --- Credit Score ---
    private int pCreditScore;

    @PropertyName("pCreditScore")
    public int getpCreditScore() { return pCreditScore; }
    @PropertyName("pCreditScore")
    public void setpCreditScore(int pCreditScore) { this.pCreditScore = pCreditScore; }

    // --- Images ---
    private List<String> pImages = new ArrayList<>();

    @PropertyName("pImages")
    public List<String> getpImages() { return pImages; }
    @PropertyName("pImages")
    public void setpImages(List<String> pImages) { this.pImages = pImages; }

    // --- Seller Info ---
    private String sellerEmail;

    // This usually maps fine, but explicit is safer
    @PropertyName("sellerEmail")
    public String getSellerEmail() { return sellerEmail; }
    @PropertyName("sellerEmail")
    public void setSellerEmail(String sellerEmail) { this.sellerEmail = sellerEmail; }

    // --- AI Analysis Fields (These were already working as per your logs) ---
    private String category;
    private String subCategory;
    private List<String> keywords = new ArrayList<>();
    private double confidence;
    private String mode;

    @PropertyName("category")
    public String getCategory() { return category; }
    @PropertyName("category")
    public void setCategory(String category) { this.category = category; }

    @PropertyName("subCategory")
    public String getSubCategory() { return subCategory; }
    @PropertyName("subCategory")
    public void setSubCategory(String subCategory) { this.subCategory = subCategory; }

    @PropertyName("keywords")
    public List<String> getKeywords() { return keywords; }
    @PropertyName("keywords")
    public void setKeywords(List<String> keywords) { this.keywords = keywords; }

    @PropertyName("confidence")
    public double getConfidence() { return confidence; }
    @PropertyName("confidence")
    public void setConfidence(double confidence) { this.confidence = confidence; }

    @PropertyName("mode")
    public String getMode() { return mode; }
    @PropertyName("mode")
    public void setMode(String mode) { this.mode = mode; }

    // --- Constructors ---
    public Product() { }

    public Product(String pId, String pName, int pMrp, int pPurchasingPrice, int pSellingPrice,
                   String pDescription, String pBrandName, int pCreditScore, String sellerEmail,
                   List<String> pImages, String category, String subCategory,
                   List<String> keywords, double confidence, String mode) {
        this.pId = pId;
        this.pName = pName;
        this.pMrp = pMrp;
        this.pPurchasingPrice = pPurchasingPrice;
        this.pSellingPrice = pSellingPrice;
        this.pDescription = pDescription;
        this.pBrandName = pBrandName;
        this.pCreditScore = pCreditScore;
        this.sellerEmail = sellerEmail;
        this.pImages = pImages;
        this.category = category;
        this.subCategory = subCategory;
        this.keywords = keywords;
        this.confidence = confidence;
        this.mode = mode;
    }
}