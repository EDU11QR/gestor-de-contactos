package com.gestordecontactos.exception;

public class FalloConsumoConfirmacionException extends RuntimeException {

    public FalloConsumoConfirmacionException(Throwable causa) {
        super("No se pudo consumir la confirmación de eliminación", causa);
    }
}
