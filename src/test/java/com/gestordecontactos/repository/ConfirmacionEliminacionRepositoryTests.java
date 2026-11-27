package com.gestordecontactos.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

class ConfirmacionEliminacionRepositoryTests {

    private NamedParameterJdbcTemplate jdbcTemplate;
    private ConfirmacionEliminacionRepository repositorio;

    @BeforeEach
    void inicializar() {
        jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        repositorio = new ConfirmacionEliminacionRepository(jdbcTemplate);
    }

    @Test
    void reemplazarEjecutaUpsertConParametrosDeSesionContactoHashYTtl() {
        when(jdbcTemplate.update(anyString(), any(SqlParameterSource.class))).thenReturn(2);

        repositorio.reemplazar("sesion-1", "hash", 7L, 10L);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<SqlParameterSource> parametros = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbcTemplate).update(sql.capture(), parametros.capture());
        assertTrue(sql.getValue().contains("INSERT INTO confirmaciones_eliminacion"));
        assertTrue(sql.getValue().contains("ON DUPLICATE KEY UPDATE"));
        assertTrue(sql.getValue().contains("INTERVAL :ttlMinutos MINUTE"));
        assertEquals("sesion-1", parametros.getValue().getValue("sessionId"));
        assertEquals("hash", parametros.getValue().getValue("tokenHash"));
        assertEquals(7L, parametros.getValue().getValue("idContacto"));
        assertEquals(10L, parametros.getValue().getValue("ttlMinutos"));
    }

    @Test
    void consumirEjecutaUpdateCondicionalYDevuelveUnaFila() {
        when(jdbcTemplate.update(anyString(), any(SqlParameterSource.class))).thenReturn(1);

        int filas = repositorio.consumir("sesion-1", "hash", 7L);

        assertEquals(1, filas);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<SqlParameterSource> parametros = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbcTemplate).update(sql.capture(), parametros.capture());
        assertTrue(sql.getValue().contains("UPDATE confirmaciones_eliminacion"));
        assertTrue(sql.getValue().contains("session_id = :sessionId"));
        assertTrue(sql.getValue().contains("id_contacto = :idContacto"));
        assertTrue(sql.getValue().contains("token_hash = :tokenHash"));
        assertTrue(sql.getValue().contains("consumido_en IS NULL"));
        assertTrue(sql.getValue().contains("expira_en > CURRENT_TIMESTAMP(3)"));
        assertTrue(sql.getValue().contains("LIMIT 1"));
        assertEquals("sesion-1", parametros.getValue().getValue("sessionId"));
        assertEquals("hash", parametros.getValue().getValue("tokenHash"));
        assertEquals(7L, parametros.getValue().getValue("idContacto"));
    }

    @Test
    void consumirDevuelveCeroCuandoNoHayFila() {
        when(jdbcTemplate.update(anyString(), any(SqlParameterSource.class))).thenReturn(0);

        assertEquals(0, repositorio.consumir("sesion-1", "hash", 7L));
    }

    @Test
    void consumirDevuelveElNumeroInesperadoSinInterpretarloComoExito() {
        when(jdbcTemplate.update(anyString(), any(SqlParameterSource.class))).thenReturn(2);

        assertEquals(2, repositorio.consumir("sesion-1", "hash", 7L));
    }
}
