package com.gestordecontactos.exception;

public class ValidacionDominioException extends RuntimeException {

    public ValidacionDominioException(Throwable causa) {
        super("Los datos enviados no son válidos.", causa);
    }
}
