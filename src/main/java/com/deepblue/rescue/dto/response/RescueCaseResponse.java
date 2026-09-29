package com.deepblue.rescue.dto.response;

import com.deepblue.rescue.domain.RescueStatus;

import java.time.LocalDate;

/**
 * DTO de salida para RescueCase. No expone RescueCenter ni Animal
 * directamente (evitaría acoplamiento y problemas de Lazy Loading fuera
 * de la sesión de Hibernate); en su lugar expone sus códigos de negocio.
 */
public record RescueCaseResponse(

        Long id,

        String caseCode,

        LocalDate rescueDate,

        String rescueLocation,

        RescueStatus status,

        String centerCode,

        String animalCode

) {
}
