package com.gestordecontactos.exception;

public class ConfirmacionInvalidaException extends RuntimeException {

    public ConfirmacionInvalidaException() {
        super("La confirmación de eliminación no es válida");
    }
}
