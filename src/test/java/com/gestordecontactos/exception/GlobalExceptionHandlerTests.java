package com.gestordecontactos.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;

import java.sql.SQLIntegrityConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class GlobalExceptionHandlerTests {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void inicializar() {
        handler = new GlobalExceptionHandler(mock(com.gestordecontactos.service.ContactoService.class),
                new IntegridadExceptionTranslator());
    }

    @Test
    void conflictoDeIntegridadOcultaDetallesTecnicos() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException("save",
                new SQLIntegrityConstraintViolationException(
                        "Duplicate entry '12345678' for key 'contactos.uq_contactos_dni'"));

        ResponseEntity<String> respuesta = handler.integridadViolada(ex);

        assertEquals(HttpStatus.CONFLICT, respuesta.getStatusCode());
        assertSinDetalles(respuesta.getBody());
    }

    @Test
    void conflictoControladoOcultaDetallesTecnicos() {
        ConflictoIntegridadException ex = new ConflictoIntegridadException(
                new IllegalStateException("constraint uq_telefonos_contacto_numero en contactos"));

        ResponseEntity<String> respuesta = handler.conflictoIntegridad(ex);

        assertEquals(HttpStatus.CONFLICT, respuesta.getStatusCode());
        assertSinDetalles(respuesta.getBody());
    }

    @Test
    void validacionOcultaDetallesTecnicos() {
        ValidacionDominioException ex = new ValidacionDominioException(
                new IllegalArgumentException("El campo dni no cumple el patrón"));

        ResponseEntity<String> respuesta = handler.validacionDominio(ex);

        assertEquals(HttpStatus.BAD_REQUEST, respuesta.getStatusCode());
        assertSinDetalles(respuesta.getBody());
    }

    private void assertSinDetalles(String cuerpo) {
        assertFalse(cuerpo.contains("uq_"));
        assertFalse(cuerpo.contains("contactos"));
        assertFalse(cuerpo.contains("Duplicate"));
        assertFalse(cuerpo.contains("dni"));
    }
}
