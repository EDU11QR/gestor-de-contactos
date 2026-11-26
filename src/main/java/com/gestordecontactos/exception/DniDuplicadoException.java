package com.gestordecontactos.exception;

public class DniDuplicadoException extends RuntimeException {

    public DniDuplicadoException(String dni) {
        super("Ya existe un contacto con el DNI " + dni);
    }
}
