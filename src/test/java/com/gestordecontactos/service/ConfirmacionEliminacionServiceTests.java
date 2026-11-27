package com.gestordecontactos.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.gestordecontactos.exception.ConfirmacionInvalidaException;
import com.gestordecontactos.repository.ConfirmacionEliminacionRepository;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

class ConfirmacionEliminacionServiceTests {

    private static final String TOKEN_VALIDO = "A".repeat(43);

    @Test
    void generarCreaTokenBase64UrlDe32BytesYGuardaSoloElHash() {
        ConfirmacionEliminacionRepository repositorio = mock(ConfirmacionEliminacionRepository.class);
        ConfirmacionEliminacionService servicio = new ConfirmacionEliminacionService(repositorio, 10L);

        String token = servicio.generar(7L, "sesion-1");

        assertTrue(token.matches("[A-Za-z0-9_-]{43}"));
        assertEquals(32, Base64.getUrlDecoder().decode(token).length);
        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        verify(repositorio).reemplazar(eq("sesion-1"), hash.capture(), eq(7L), eq(10L));
        assertEquals(64, hash.getValue().length());
        assertEquals(sha256(token), hash.getValue());
    }

    @Test
    void generarUsaElTtlConfigurado() {
        ConfirmacionEliminacionRepository repositorio = mock(ConfirmacionEliminacionRepository.class);
        ConfirmacionEliminacionService servicio = new ConfirmacionEliminacionService(repositorio, 15L);

        servicio.generar(7L, "sesion-1");

        verify(repositorio).reemplazar(anyString(), anyString(), anyLong(), eq(15L));
    }

    @Test
    void constructorRechazaTtlNoPositivo() {
        ConfirmacionEliminacionRepository repositorio = mock(ConfirmacionEliminacionRepository.class);

        assertThrows(IllegalArgumentException.class,
                () -> new ConfirmacionEliminacionService(repositorio, 0L));
    }

    @Test
    void consumirConUnaFilaAfectadaNoLanzaExcepcion() {
        ConfirmacionEliminacionRepository repositorio = mock(ConfirmacionEliminacionRepository.class);
        when(repositorio.consumir(anyString(), anyString(), anyLong())).thenReturn(1);
        ConfirmacionEliminacionService servicio = new ConfirmacionEliminacionService(repositorio, 10L);

        assertDoesNotThrow(() -> servicio.consumir(7L, "sesion-1", TOKEN_VALIDO));

        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        verify(repositorio).consumir(eq("sesion-1"), hash.capture(), eq(7L));
        assertEquals(sha256(TOKEN_VALIDO), hash.getValue());
    }

    @Test
    void consumirConCeroFilasLanzaConfirmacionInvalida() {
        ConfirmacionEliminacionRepository repositorio = mock(ConfirmacionEliminacionRepository.class);
        when(repositorio.consumir(anyString(), anyString(), anyLong())).thenReturn(0);
        ConfirmacionEliminacionService servicio = new ConfirmacionEliminacionService(repositorio, 10L);

        assertThrows(ConfirmacionInvalidaException.class,
                () -> servicio.consumir(7L, "sesion-1", TOKEN_VALIDO));
    }

    @Test
    void consumirConFilasInesperadasLanzaConfirmacionInvalida() {
        ConfirmacionEliminacionRepository repositorio = mock(ConfirmacionEliminacionRepository.class);
        when(repositorio.consumir(anyString(), anyString(), anyLong())).thenReturn(2);
        ConfirmacionEliminacionService servicio = new ConfirmacionEliminacionService(repositorio, 10L);

        assertThrows(ConfirmacionInvalidaException.class,
                () -> servicio.consumir(7L, "sesion-1", TOKEN_VALIDO));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            " ",
            "***",
            "abc def",
            "AAAA",
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
    })
    void consumirRechazaTokensMalformadosSinConsultarRepositorio(String token) {
        ConfirmacionEliminacionRepository repositorio = mock(ConfirmacionEliminacionRepository.class);
        ConfirmacionEliminacionService servicio = new ConfirmacionEliminacionService(repositorio, 10L);

        assertThrows(ConfirmacionInvalidaException.class,
                () -> servicio.consumir(7L, "sesion-1", token));

        verifyNoInteractions(repositorio);
    }

    @Test
    void consumirUsaTransacionRequiresNew() throws NoSuchMethodException {
        Method metodo = ConfirmacionEliminacionService.class
                .getMethod("consumir", Long.class, String.class, String.class);
        Transactional transaccion = metodo.getAnnotation(Transactional.class);

        assertNotNull(transaccion);
        assertEquals(Propagation.REQUIRES_NEW, transaccion.propagation());
    }

    private String sha256(String token) {
        try {
            byte[] resumen = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.US_ASCII));
            return HexFormat.of().formatHex(resumen);
        } catch (NoSuchAlgorithmException ex) {
            throw new AssertionError(ex);
        }
    }
}
