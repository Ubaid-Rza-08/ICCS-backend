package com.ubaid.Auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Data transfer object for submitting a review")
public class ReviewRequestDto {

    @Schema(description = "The ID of the product being reviewed", example = "URBANIKE452")
    private String productId;

    @Schema(description = "Rating between 0 and 5", example = "5")
    private int rating;

    @Schema(description = "The text content of the review", example = "Great product, highly recommended!")
    private String message;
}