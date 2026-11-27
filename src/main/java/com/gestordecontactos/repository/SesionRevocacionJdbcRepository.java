package com.gestordecontactos.repository;

import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SesionRevocacionJdbcRepository {

    private static final Pattern IDENTIFICADOR_SEGURO = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");

    private final JdbcTemplate jdbcTemplate;
    private final String sql;

    public SesionRevocacionJdbcRepository(JdbcTemplate jdbcTemplate,
            @Value("${spring.session.jdbc.table-name:SPRING_SESSION}") String nombreTabla) {
        if (nombreTabla == null || !IDENTIFICADOR_SEGURO.matcher(nombreTabla).matches()) {
            throw new IllegalArgumentException("El nombre de la tabla de sesiones no es válido");
        }
        this.jdbcTemplate = jdbcTemplate;
        this.sql = "DELETE FROM " + nombreTabla + " WHERE PRINCIPAL_NAME = ?";
    }

    public int eliminarPorPrincipalName(String principalName) {
        if (principalName == null || principalName.isBlank()) {
            throw new IllegalArgumentException("El principal de la sesión es obligatorio");
        }
        return jdbcTemplate.update(sql, principalName);
    }

    String sql() {
        return sql;
    }
}
