package com.gestordecontactos.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.gestordecontactos.entity.Usuario;
import com.gestordecontactos.repository.SesionRevocacionJdbcRepository;
import com.gestordecontactos.repository.UsuarioRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UsuarioRevocacionServiceTests {

    private UsuarioRepository usuarioRepository;
    private SesionRevocacionJdbcRepository sesionRepository;
    private UsuarioRevocacionService service;

    @BeforeEach
    void inicializar() {
        usuarioRepository = mock(UsuarioRepository.class);
        sesionRepository = mock(SesionRevocacionJdbcRepository.class);
        service = new UsuarioRevocacionService(usuarioRepository, sesionRepository);
    }

    @Test
    void revocaUsuarioActivoNormalizandoEmailYEliminandoSesiones() {
        Usuario usuario = usuario("usuario@ejemplo.com", true);
        when(usuarioRepository.findByEmailForUpdate("usuario@ejemplo.com")).thenReturn(Optional.of(usuario));

        service.revocar(" USUARIO@Ejemplo.COM ");

        assertFalse(usuario.isActivo());
        verify(usuarioRepository).findByEmailForUpdate("usuario@ejemplo.com");
        verify(usuarioRepository).save(usuario);
        verify(sesionRepository).eliminarPorPrincipalName("usuario@ejemplo.com");
    }

    @Test
    void usuarioInexistenteNoHaceNada() {
        when(usuarioRepository.findByEmailForUpdate("ausente@ejemplo.com")).thenReturn(Optional.empty());

        service.revocar(" AUSENTE@Ejemplo.COM ");

        verify(usuarioRepository, never()).save(any());
        verify(sesionRepository, never()).eliminarPorPrincipalName(any());
    }

    @Test
    void revocacionRepetidaEliminaSesionesSinVolverAGuardar() {
        Usuario usuario = usuario("usuario@ejemplo.com", false);
        when(usuarioRepository.findByEmailForUpdate("usuario@ejemplo.com")).thenReturn(Optional.of(usuario));

        service.revocar("usuario@ejemplo.com");

        verify(usuarioRepository, never()).save(any());
        verify(sesionRepository).eliminarPorPrincipalName("usuario@ejemplo.com");
    }

    @Test
    void rechazaEmailSuperiorACienCaracteres() {
        String emailLargo = "a".repeat(101);

        assertThrows(IllegalArgumentException.class, () -> service.revocar(emailLargo));

        verifyNoInteractions(usuarioRepository, sesionRepository);
    }

    @Test
    void propagaFalloAlEliminarSesionesParaPermitirRollback() {
        Usuario usuario = usuario("usuario@ejemplo.com", true);
        when(usuarioRepository.findByEmailForUpdate("usuario@ejemplo.com")).thenReturn(Optional.of(usuario));
        doThrow(new IllegalStateException("fallo JDBC"))
                .when(sesionRepository).eliminarPorPrincipalName("usuario@ejemplo.com");

        assertThrows(IllegalStateException.class, () -> service.revocar("usuario@ejemplo.com"));

        verify(usuarioRepository).save(usuario);
    }

    @Test
    void emailVacioNoHaceNada() {
        service.revocar("   ");

        verifyNoInteractions(usuarioRepository, sesionRepository);
    }

    private Usuario usuario(String email, boolean activo) {
        Usuario usuario = new Usuario();
        usuario.setId(1L);
        usuario.setEmail(email);
        usuario.setNombre("Usuario");
        usuario.setPasswordHash("hash");
        usuario.setActivo(activo);
        return usuario;
    }
}
