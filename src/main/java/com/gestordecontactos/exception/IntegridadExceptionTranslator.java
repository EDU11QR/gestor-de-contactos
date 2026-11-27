package com.gestordecontactos.exception;

import jakarta.validation.ConstraintViolationException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Component
public class IntegridadExceptionTranslator {

    private static final String RESTRICCION_DNI = "uq_contactos_dni";

    public RuntimeException traducirIntegridad(DataIntegrityViolationException ex, String dni) {
        if (esRestriccionDni(ex) && dni != null && !dni.isBlank()) {
            return new DniDuplicadoException(dni, ex);
        }
        return new ConflictoIntegridadException(ex);
    }

    public ValidacionDominioException traducirValidacion(ConstraintViolationException ex) {
        return new ValidacionDominioException(ex);
    }

    public boolean esRestriccionDni(Throwable ex) {
        Set<Throwable> visitadas = Collections.newSetFromMap(new IdentityHashMap<>());
        Throwable causa = ex;
        while (causa != null && visitadas.add(causa)) {
            if (nombreRestriccion(causa).contains(RESTRICCION_DNI)) {
                return true;
            }
            causa = causa.getCause();
        }
        return false;
    }

    private String nombreRestriccion(Throwable causa) {
        if (causa instanceof org.hibernate.exception.ConstraintViolationException violacionHibernate) {
            String nombre = violacionHibernate.getConstraintName();
            if (nombre != null && !nombre.isBlank()) {
                return nombre;
            }
        }
        String mensaje = causa.getMessage();
        if (mensaje == null) {
            return "";
        }
        if (causa instanceof SQLIntegrityConstraintViolationException || causa instanceof DataIntegrityViolationException) {
            return mensaje;
        }
        return "";
    }
}
