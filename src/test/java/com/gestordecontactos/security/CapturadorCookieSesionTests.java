package com.gestordecontactos.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

class CapturadorCookieSesionTests {

    private static final String ID_SESION = "id-sesion-capturada";
    private static final String COOKIE_SESION = "GESTOR_SESSION="
            + Base64.getEncoder().encodeToString(ID_SESION.getBytes(StandardCharsets.UTF_8))
            + "; Path=/; HttpOnly; Secure; SameSite=Lax";

    @Test
    void publicaTodasLasCabecerasCuandoLaOperacionSeConfirma() throws Exception {
        MockHttpServletResponse respuesta = new MockHttpServletResponse();
        AutenticacionRevocacionFilter.CapturadorCookieSesion capturador =
                new AutenticacionRevocacionFilter.CapturadorCookieSesion(respuesta, "GESTOR_SESSION");

        capturador.addHeader("Set-Cookie", COOKIE_SESION);
        capturador.addHeader("X-Prueba", "confirmado");
        capturador.publicar(true);

        assertEquals("confirmado", respuesta.getHeader("X-Prueba"));
        assertTrue(respuesta.getHeader("Set-Cookie").contains("GESTOR_SESSION="));
        assertTrue(respuesta.getHeader("Set-Cookie").contains("Secure"));
    }

    @Test
    void suprimeSoloLaCookieDeSesionCuandoSeRevierte() throws Exception {
        MockHttpServletResponse respuesta = new MockHttpServletResponse();
        AutenticacionRevocacionFilter.CapturadorCookieSesion capturador =
                new AutenticacionRevocacionFilter.CapturadorCookieSesion(respuesta, "GESTOR_SESSION");

        capturador.addHeader("Set-Cookie", COOKIE_SESION);
        capturador.addHeader("Set-Cookie", "OTRA_COOKIE=valor");
        capturador.addHeader("X-Prueba", "revertido");

        assertEquals(List.of(COOKIE_SESION, "OTRA_COOKIE=valor"), capturador.getHeaders("Set-Cookie"));
        assertEquals(ID_SESION, capturador.idSesion);

        capturador.publicar(false);

        assertEquals("revertido", respuesta.getHeader("X-Prueba"));
        assertEquals("OTRA_COOKIE=valor", respuesta.getHeader("Set-Cookie"));
        assertEquals(1, respuesta.getHeaders("Set-Cookie").size());
        assertNull(respuesta.getHeader("X-Inexistente"));
    }

    @Test
    void difiereLaRedireccionHastaQueLaSesionQuedaConfirmada() throws Exception {
        MockHttpServletResponse respuesta = new MockHttpServletResponse();
        AutenticacionRevocacionFilter.CapturadorCookieSesion capturador =
                new AutenticacionRevocacionFilter.CapturadorCookieSesion(respuesta, "GESTOR_SESSION");

        capturador.addHeader("Set-Cookie", COOKIE_SESION);
        capturador.sendRedirect("/");
        assertTrue(!respuesta.isCommitted());

        capturador.publicar(true);

        assertEquals("/", respuesta.getRedirectedUrl());
        assertEquals(302, respuesta.getStatus());
        assertTrue(respuesta.getHeader("Set-Cookie").contains("GESTOR_SESSION="));
    }
}
