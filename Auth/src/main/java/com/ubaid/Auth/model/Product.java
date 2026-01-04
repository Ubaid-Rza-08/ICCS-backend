package com.ubaid.Auth.model;

import java.util.ArrayList;
import java.util.List;

public class Product {
    private String pId;
    private String pName;
    private int pMrp;
    private int pPurchasingPrice;
    private int pSellingPrice;
    private String pDescription;
    private String pBrandName;
    private int pCreditScore;
    private String sellerEmail;

    // CHANGED: Store a list of Image URLs
    private List<String> pImages = new ArrayList<>();

    public Product() { }

    public Product(String pId, String pName, int pMrp, int pPurchasingPrice, int pSellingPrice,
                   String pDescription, String pBrandName, int pCreditScore, String sellerEmail, List<String> pImages) {
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
    }

    // Getters and Setters for pImages
    public List<String> getpImages() { return pImages; }
    public void setpImages(List<String> pImages) { this.pImages = pImages; }

    // Existing Getters and Setters...
    public String getSellerEmail() { return sellerEmail; }
    public void setSellerEmail(String sellerEmail) { this.sellerEmail = sellerEmail; }
    public String getpId() { return pId; }
    public void setpId(String pId) { this.pId = pId; }
    public String getpName() { return pName; }
    public void setpName(String pName) { this.pName = pName; }
    public int getpMrp() { return pMrp; }
    public void setpMrp(int pMrp) { this.pMrp = pMrp; }
    public int getpPurchasingPrice() { return pPurchasingPrice; }
    public void setpPurchasingPrice(int pPurchasingPrice) { this.pPurchasingPrice = pPurchasingPrice; }
    public int getpSellingPrice() { return pSellingPrice; }
    public void setpSellingPrice(int pSellingPrice) { this.pSellingPrice = pSellingPrice; }
    public String getpDescription() { return pDescription; }
    public void setpDescription(String pDescription) { this.pDescription = pDescription; }
    public String getpBrandName() { return pBrandName; }
    public void setpBrandName(String pBrandName) { this.pBrandName = pBrandName; }
    public int getpCreditScore() { return pCreditScore; }
    public void setpCreditScore(int pCreditScore) { this.pCreditScore = pCreditScore; }
}