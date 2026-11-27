package com.gestordecontactos.config;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.gestordecontactos.security.UsuarioRevocacionService;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.Order;

class UsuarioRevocacionRunnerTests {

    @Test
    void ejecutaRevocacionCuandoHayEmailConfigurado() {
        UsuarioRevocacionService service = mock(UsuarioRevocacionService.class);
        UsuarioRevocacionRunner runner = new UsuarioRevocacionRunner(service, " USUARIO@Ejemplo.COM ");

        runner.run(null);

        verify(service).revocar(" USUARIO@Ejemplo.COM ");
    }

    @Test
    void noEjecutaRevocacionSinEmail() {
        UsuarioRevocacionService service = mock(UsuarioRevocacionService.class);
        UsuarioRevocacionRunner runner = new UsuarioRevocacionRunner(service, "  ");

        runner.run(null);

        verifyNoInteractions(service);
    }

    @Test
    void aprovisionamientoSeEjecutaAntesQueRevocacion() {
        Order aprovisionamiento = UsuarioProvisioningRunner.class.getAnnotation(Order.class);
        Order revocacion = UsuarioRevocacionRunner.class.getAnnotation(Order.class);

        assertNotNull(aprovisionamiento);
        assertNotNull(revocacion);
        assertTrue(aprovisionamiento.value() < revocacion.value());
    }
}
