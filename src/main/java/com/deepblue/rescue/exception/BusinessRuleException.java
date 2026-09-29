package com.deepblue.rescue.exception;

/**
 * Se lanza cuando el recurso existe pero la operación solicitada no está
 * permitida por una regla de negocio.
 * Ejemplo: "Cannot register treatment because the animal has already been released."
 */
public class BusinessRuleException
        extends RuntimeException {

    public BusinessRuleException(
            String message) {

        super(message);
    }
}
