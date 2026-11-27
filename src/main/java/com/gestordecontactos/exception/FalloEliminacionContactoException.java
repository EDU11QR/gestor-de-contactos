package com.gestordecontactos.exception;

public class FalloEliminacionContactoException extends RuntimeException {

    public FalloEliminacionContactoException(Throwable causa) {
        super("No se pudo eliminar el contacto", causa);
    }
}
