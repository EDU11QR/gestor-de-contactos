package com.gestordecontactos.exception;

public class DniDuplicadoException extends RuntimeException {

    public DniDuplicadoException(String dni) {
        this(dni, null);
    }

    public DniDuplicadoException(String dni, Throwable causa) {
        super("Ya existe un contacto con ese DNI.", causa);
    }
}
