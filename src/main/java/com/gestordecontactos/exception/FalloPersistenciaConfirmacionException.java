package com.gestordecontactos.exception;

public class FalloPersistenciaConfirmacionException extends RuntimeException {

    public FalloPersistenciaConfirmacionException(Throwable causa) {
        super("No se pudo registrar la confirmación de eliminación", causa);
    }
}
