package com.gestordecontactos.exception;

public class ConflictoIntegridadException extends RuntimeException {

    public ConflictoIntegridadException(Throwable causa) {
        super("No se pudo completar la operación por un conflicto de datos.", causa);
    }
}
