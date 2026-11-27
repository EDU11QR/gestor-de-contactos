package com.gestordecontactos.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gestordecontactos.exception.ConfirmacionInvalidaException;
import com.gestordecontactos.exception.FalloConsumoConfirmacionException;
import com.gestordecontactos.exception.FalloEliminacionContactoException;
import com.gestordecontactos.exception.FalloPersistenciaConfirmacionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.dao.DataRetrievalFailureException;

class EliminacionContactoServiceTests {

    private ConfirmacionEliminacionService confirmacionService;
    private ContactoService contactoService;
    private EliminacionContactoService servicio;

    @BeforeEach
    void inicializar() {
        confirmacionService = mock(ConfirmacionEliminacionService.class);
        contactoService = mock(ContactoService.class);
        servicio = new EliminacionContactoService(confirmacionService, contactoService);
    }

    @Test
    void eliminarConsumeLaConfirmacionAntesDeEliminarElContacto() {
        servicio.eliminar(7L, "sesion-1", "token");

        InOrder orden = inOrder(confirmacionService, contactoService);
        orden.verify(confirmacionService).consumir(7L, "sesion-1", "token");
        orden.verify(contactoService).eliminar(7L);
    }

    @Test
    void generarDelegaEnElServicioDeConfirmacion() {
        when(confirmacionService.generar(7L, "sesion-1")).thenReturn("token-generado");

        String token = servicio.generar(7L, "sesion-1");

        assertEquals("token-generado", token);
        verify(confirmacionService).generar(7L, "sesion-1");
    }

    @Test
    void generarTraduceFalloDePersistenciaYConservaLaCausa() {
        DataRetrievalFailureException causa = new DataRetrievalFailureException("base de datos");
        when(confirmacionService.generar(7L, "sesion-1")).thenThrow(causa);

        FalloPersistenciaConfirmacionException ex = assertThrows(
                FalloPersistenciaConfirmacionException.class,
                () -> servicio.generar(7L, "sesion-1"));

        assertSame(causa, ex.getCause());
    }

    @Test
    void confirmacionInvalidaSePropagaYNoEliminaElContacto() {
        doThrow(new ConfirmacionInvalidaException())
                .when(confirmacionService).consumir(7L, "sesion-1", "token");

        assertThrows(ConfirmacionInvalidaException.class,
                () -> servicio.eliminar(7L, "sesion-1", "token"));

        verify(contactoService, never()).eliminar(anyLong());
    }

    @Test
    void falloAlConsumirSeTraduceYNoEliminaElContacto() {
        DataRetrievalFailureException causa = new DataRetrievalFailureException("base de datos");
        doThrow(causa).when(confirmacionService).consumir(7L, "sesion-1", "token");

        FalloConsumoConfirmacionException ex = assertThrows(
                FalloConsumoConfirmacionException.class,
                () -> servicio.eliminar(7L, "sesion-1", "token"));

        assertSame(causa, ex.getCause());
        verify(contactoService, never()).eliminar(anyLong());
    }

    @Test
    void falloNoPersistenteDeLaConfirmacionNoSeEnvuelveYNoEliminaElContacto() {
        IllegalStateException causa = new IllegalStateException("fallo interno");
        doThrow(causa).when(confirmacionService).consumir(7L, "sesion-1", "token");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> servicio.eliminar(7L, "sesion-1", "token"));

        assertSame(causa, ex);
        verify(contactoService, never()).eliminar(anyLong());
    }

    @Test
    void falloAlEliminarSeTraduceYConservaLaCausa() {
        RuntimeException causa = new RuntimeException("fallo al borrar");
        doThrow(causa).when(contactoService).eliminar(7L);

        FalloEliminacionContactoException ex = assertThrows(
                FalloEliminacionContactoException.class,
                () -> servicio.eliminar(7L, "sesion-1", "token"));

        assertSame(causa, ex.getCause());
        verify(confirmacionService).consumir(eq(7L), eq("sesion-1"), eq("token"));
    }
}
