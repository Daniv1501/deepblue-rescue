package com.deepblue.rescue.dto.response;

/**
 * DTO de salida para GET /api/animals/{animalCode}/treatment-eligibility.
 */
public record TreatmentEligibilityResponse(
        String animalCode,
        boolean eligible
) {
}
