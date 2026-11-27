package com.gestordecontactos.repository;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ConfirmacionEliminacionRepository {

    private static final String REEMPLAZAR_SQL = """
            INSERT INTO confirmaciones_eliminacion
                (session_id, token_hash, id_contacto, creado_en, expira_en, consumido_en)
            VALUES
                (:sessionId, :tokenHash, :idContacto,
                 CURRENT_TIMESTAMP(3),
                 DATE_ADD(CURRENT_TIMESTAMP(3), INTERVAL :ttlMinutos MINUTE),
                 NULL)
            AS nueva
            ON DUPLICATE KEY UPDATE
                token_hash = nueva.token_hash,
                id_contacto = nueva.id_contacto,
                creado_en = CURRENT_TIMESTAMP(3),
                expira_en = DATE_ADD(CURRENT_TIMESTAMP(3), INTERVAL :ttlMinutos MINUTE),
                consumido_en = NULL
            """;

    private static final String CONSUMIR_SQL = """
            UPDATE confirmaciones_eliminacion
            SET consumido_en = CURRENT_TIMESTAMP(3)
            WHERE session_id = :sessionId
              AND id_contacto = :idContacto
              AND token_hash = :tokenHash
              AND consumido_en IS NULL
              AND expira_en > CURRENT_TIMESTAMP(3)
            LIMIT 1
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public ConfirmacionEliminacionRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void reemplazar(String sessionId, String tokenHash, Long idContacto, long ttlMinutos) {
        MapSqlParameterSource parametros = new MapSqlParameterSource()
                .addValue("sessionId", sessionId)
                .addValue("tokenHash", tokenHash)
                .addValue("idContacto", idContacto)
                .addValue("ttlMinutos", ttlMinutos);
        jdbcTemplate.update(REEMPLAZAR_SQL, parametros);
    }

    public int consumir(String sessionId, String tokenHash, Long idContacto) {
        MapSqlParameterSource parametros = new MapSqlParameterSource()
                .addValue("sessionId", sessionId)
                .addValue("tokenHash", tokenHash)
                .addValue("idContacto", idContacto);
        return jdbcTemplate.update(CONSUMIR_SQL, parametros);
    }
}
