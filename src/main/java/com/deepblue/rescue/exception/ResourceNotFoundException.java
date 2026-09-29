package com.deepblue.rescue.exception;

/**
 * Se lanza cuando el recurso solicitado no existe.
 * Ejemplo: "Animal AN-999 does not exist."
 */
public class ResourceNotFoundException
        extends RuntimeException {

    public ResourceNotFoundException(
            String message) {

        super(message);
    }
}
