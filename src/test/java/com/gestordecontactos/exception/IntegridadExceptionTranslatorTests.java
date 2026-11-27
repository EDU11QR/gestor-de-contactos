package com.gestordecontactos.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.Set;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class IntegridadExceptionTranslatorTests {

    private IntegridadExceptionTranslator traductor;

    @BeforeEach
    void inicializar() {
        traductor = new IntegridadExceptionTranslator();
    }

    @Test
    void traduceDniDuplicadoConCausaSqlAnidada() {
        SQLIntegrityConstraintViolationException sql = new SQLIntegrityConstraintViolationException(
                "Duplicate entry '12345678' for key 'contactos.uq_contactos_dni'");
        DataIntegrityViolationException ex = new DataIntegrityViolationException("could not execute statement", sql);

        DniDuplicadoException traducido = assertInstanceOf(DniDuplicadoException.class,
                traductor.traducirIntegridad(ex, "12345678"));

        assertEquals("Ya existe un contacto con ese DNI.", traducido.getMessage());
        assertSame(ex, traducido.getCause());
        assertFalse(traducido.getMessage().contains("uq_contactos_dni"));
    }

    @Test
    void traduceRestriccionDniDeHibernate() {
        ConstraintViolationException hibernate = new ConstraintViolationException(
                "Constraint violation", new SQLException("duplicado"), "uq_contactos_dni");
        DataIntegrityViolationException ex = new DataIntegrityViolationException("save", hibernate);

        DniDuplicadoException traducido = assertInstanceOf(DniDuplicadoException.class,
                traductor.traducirIntegridad(ex, "87654321"));

        assertSame(ex, traducido.getCause());
    }

    @Test
    void traduceRestriccionDesconocidaComoConflictoGenerico() {
        SQLIntegrityConstraintViolationException sql = new SQLIntegrityConstraintViolationException(
                "Duplicate entry 'x' for key 'contactos.uq_telefonos_contacto_numero'");
        DataIntegrityViolationException ex = new DataIntegrityViolationException("save", sql);

        ConflictoIntegridadException traducido = assertInstanceOf(ConflictoIntegridadException.class,
                traductor.traducirIntegridad(ex, "12345678"));

        assertSame(ex, traducido.getCause());
        assertFalse(traducido.getMessage().contains("uq_telefonos_contacto_numero"));
        assertFalse(traducido.getMessage().contains("Duplicate entry"));
    }

    @Test
    void traduceConstraintViolationDeJakartaComoValidacion() {
        jakarta.validation.ConstraintViolationException ex =
                new jakarta.validation.ConstraintViolationException("El dni no es válido", Set.of());

        ValidacionDominioException traducido = traductor.traducirValidacion(ex);

        assertSame(ex, traducido.getCause());
        assertFalse(traducido.getMessage().contains("dni"));
    }

    @Test
    void recorreCadenaCiclicaSinBucleInfinito() {
        SQLIntegrityConstraintViolationException sql = new SQLIntegrityConstraintViolationException(
                "Duplicate entry '12345678' for key 'contactos.uq_contactos_dni'");
        DataIntegrityViolationException ex = new DataIntegrityViolationException("save", sql);
        sql.initCause(ex);

        DniDuplicadoException traducido = assertInstanceOf(DniDuplicadoException.class,
                traductor.traducirIntegridad(ex, "12345678"));

        assertSame(ex, traducido.getCause());
    }

    @Test
    void noClasificaExcepcionesInesperadasComoIntegridad() {
        IllegalStateException ex = new IllegalStateException("uq_contactos_dni");

        assertFalse(traductor.esRestriccionDni(ex));
    }

    @Test
    void conservaConflictoGenericoCuandoNoHayDniDisponible() {
        SQLIntegrityConstraintViolationException sql = new SQLIntegrityConstraintViolationException(
                "Duplicate entry '12345678' for key 'contactos.uq_contactos_dni'");
        DataIntegrityViolationException ex = new DataIntegrityViolationException("save", sql);

        RuntimeException traducido = traductor.traducirIntegridad(ex, null);

        assertTrue(traducido instanceof ConflictoIntegridadException);
        assertSame(ex, traducido.getCause());
    }
}
