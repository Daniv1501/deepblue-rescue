package com.deepblue.rescue.dto.response;

import com.deepblue.rescue.domain.AnimalSex;
import com.deepblue.rescue.domain.RescueStatus;

/**
 * DTO de salida para Animal (Parte XII — Reto del estudiante).
 * caseCode y rescueStatus provienen de animal.rescueCase, pero no se
 * expone la entidad RescueCase completa.
 */
public record AnimalResponse(

        Long id,

        String animalCode,

        String commonName,

        String scientificName,

        AnimalSex sex,

        String caseCode,

        RescueStatus rescueStatus

) {
}
