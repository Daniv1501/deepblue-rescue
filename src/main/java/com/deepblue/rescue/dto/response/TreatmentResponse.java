package com.deepblue.rescue.dto.response;

import com.deepblue.rescue.domain.TreatmentType;

import java.time.LocalDateTime;

/**
 * DTO de salida para Treatment. No expone Animal ni Specialist
 * directamente; expone sus códigos de negocio (animalCode, specialistCode).
 */
public record TreatmentResponse(

        Long id,

        String animalCode,

        String specialistCode,

        LocalDateTime performedAt,

        TreatmentType type,

        String description

) {
}
