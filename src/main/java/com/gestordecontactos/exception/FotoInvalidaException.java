package com.gestordecontactos.exception;

public class FotoInvalidaException extends RuntimeException {

    public FotoInvalidaException(String mensaje) {
        super(mensaje);
    }

    public FotoInvalidaException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
