package com.gestordecontactos.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class SesionRevocacionJdbcRepositoryTests {

    private static final String SQL = "DELETE FROM SPRING_SESSION WHERE PRINCIPAL_NAME = ?";

    @Test
    void eliminaSesionesUsandoSqlCentralizadoYParametro() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        SesionRevocacionJdbcRepository repositorio = new SesionRevocacionJdbcRepository(jdbcTemplate, "SPRING_SESSION");
        when(jdbcTemplate.update(SQL, "usuario@ejemplo.com")).thenReturn(2);

        assertEquals(2, repositorio.eliminarPorPrincipalName("usuario@ejemplo.com"));
        assertEquals(SQL, repositorio.sql());
        verify(jdbcTemplate).update(SQL, "usuario@ejemplo.com");
    }

    @Test
    void rechazaNombreDeTablaInvalido() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

        assertThrows(IllegalArgumentException.class,
                () -> new SesionRevocacionJdbcRepository(jdbcTemplate, "SPRING_SESSION; DROP TABLE usuarios"));
    }

    @Test
    void rechazaPrincipalVacio() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        SesionRevocacionJdbcRepository repositorio = new SesionRevocacionJdbcRepository(jdbcTemplate, "SPRING_SESSION");

        assertThrows(IllegalArgumentException.class, () -> repositorio.eliminarPorPrincipalName("  "));
    }
}
