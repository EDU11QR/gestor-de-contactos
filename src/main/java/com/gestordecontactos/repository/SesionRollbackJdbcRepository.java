package com.gestordecontactos.repository;

import java.util.List;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class SesionRollbackJdbcRepository {

    private static final Pattern IDENTIFICADOR_SEGURO = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");

    private final JdbcTemplate jdbcTemplate;
    private final String sqlSesion;
    private final String sqlAtributos;
    private final String sqlActualizar;
    private final String sqlBorrarAtributos;
    private final String sqlInsertarAtributo;

    public SesionRollbackJdbcRepository(JdbcTemplate jdbcTemplate,
            @Value("${spring.session.jdbc.table-name:SPRING_SESSION}") String nombreTabla) {
        if (nombreTabla == null || !IDENTIFICADOR_SEGURO.matcher(nombreTabla).matches()) {
            throw new IllegalArgumentException("El nombre de la tabla de sesiones no es válido");
        }
        this.jdbcTemplate = jdbcTemplate;
        String tablaAtributos = nombreTabla + "_ATTRIBUTES";
        this.sqlSesion = "SELECT PRIMARY_ID, SESSION_ID, LAST_ACCESS_TIME, MAX_INACTIVE_INTERVAL, "
                + "EXPIRY_TIME, PRINCIPAL_NAME FROM " + nombreTabla + " WHERE SESSION_ID = ?";
        this.sqlAtributos = "SELECT ATTRIBUTE_NAME, ATTRIBUTE_BYTES FROM " + tablaAtributos
                + " WHERE SESSION_PRIMARY_ID = ?";
        this.sqlActualizar = "UPDATE " + nombreTabla
                + " SET SESSION_ID = ?, LAST_ACCESS_TIME = ?, MAX_INACTIVE_INTERVAL = ?, "
                + "EXPIRY_TIME = ?, PRINCIPAL_NAME = ? WHERE PRIMARY_ID = ?";
        this.sqlBorrarAtributos = "DELETE FROM " + tablaAtributos + " WHERE SESSION_PRIMARY_ID = ?";
        this.sqlInsertarAtributo = "INSERT INTO " + tablaAtributos
                + " (SESSION_PRIMARY_ID, ATTRIBUTE_NAME, ATTRIBUTE_BYTES) VALUES (?, ?, ?)";
    }

    public SesionSnapshot buscar(String idSesion) {
        if (idSesion == null || idSesion.isBlank()) {
            return null;
        }
        List<SesionSnapshot> sesiones = jdbcTemplate.query(sqlSesion, (rs, fila) -> new SesionSnapshot(
                rs.getString("PRIMARY_ID"),
                rs.getString("SESSION_ID"),
                rs.getLong("LAST_ACCESS_TIME"),
                rs.getInt("MAX_INACTIVE_INTERVAL"),
                rs.getLong("EXPIRY_TIME"),
                rs.getString("PRINCIPAL_NAME"),
                List.of()), idSesion);
        if (sesiones.isEmpty()) {
            return null;
        }
        SesionSnapshot sesion = sesiones.get(0);
        List<AtributoSesion> atributos = jdbcTemplate.query(sqlAtributos,
                (rs, fila) -> new AtributoSesion(rs.getString("ATTRIBUTE_NAME"), rs.getBytes("ATTRIBUTE_BYTES")),
                sesion.primaryId());
        return new SesionSnapshot(sesion.primaryId(), sesion.sessionId(), sesion.lastAccessTime(),
                sesion.maxInactiveInterval(), sesion.expiryTime(), sesion.principalName(), atributos);
    }

    @Transactional
    public boolean restaurar(SesionSnapshot sesion) {
        int actualizadas = jdbcTemplate.update(sqlActualizar, sesion.sessionId(), sesion.lastAccessTime(),
                sesion.maxInactiveInterval(), sesion.expiryTime(), sesion.principalName(), sesion.primaryId());
        if (actualizadas == 0) {
            return false;
        }
        jdbcTemplate.update(sqlBorrarAtributos, sesion.primaryId());
        for (AtributoSesion atributo : sesion.atributos()) {
            jdbcTemplate.update(sqlInsertarAtributo, sesion.primaryId(), atributo.nombre(), atributo.bytes());
        }
        return true;
    }

    public record SesionSnapshot(String primaryId, String sessionId, long lastAccessTime,
            int maxInactiveInterval, long expiryTime, String principalName, List<AtributoSesion> atributos) {
    }

    public record AtributoSesion(String nombre, byte[] bytes) {
    }
}
