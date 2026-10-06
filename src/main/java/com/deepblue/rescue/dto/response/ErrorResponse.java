package com.deepblue.rescue.dto.response;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Contrato único de error de la API. Todos los errores HTTP
 * (400, 404, 409, 500) se devuelven con esta estructura.
 */
public record ErrorResponse(
        LocalDateTime timestamp,
        int status,
        String error,
        String message,
        Map<String, String> details
) {
}
