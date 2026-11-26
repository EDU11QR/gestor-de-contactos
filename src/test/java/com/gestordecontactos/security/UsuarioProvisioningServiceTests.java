package com.gestordecontactos.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gestordecontactos.entity.Usuario;
import com.gestordecontactos.repository.UsuarioRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class UsuarioProvisioningServiceTests {

    private final UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final UsuarioProvisioningService service = new UsuarioProvisioningService(usuarioRepository, encoder);

    @Test
    void creaUsuarioNormalizadoConHashBCrypt() {
        when(usuarioRepository.findByEmail("usuario@ejemplo.com")).thenReturn(Optional.empty());

        service.provisionar(" USUARIO@Ejemplo.COM ", "Clave123!", " Usuario ");

        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(captor.capture());
        Usuario guardado = captor.getValue();
        assertEquals("usuario@ejemplo.com", guardado.getEmail());
        assertEquals("Usuario", guardado.getNombre());
        assertTrue(encoder.matches("Clave123!", guardado.getPasswordHash()));
    }

    @Test
    void noModificaUsuarioExistenteAunqueCambienLaContrasenaYElNombre() {
        String hashOriginal = encoder.encode("ClaveOriginal1!");
        Usuario existente = usuario("usuario@ejemplo.com", "UsuarioOriginal", hashOriginal);
        when(usuarioRepository.findByEmail("usuario@ejemplo.com")).thenReturn(Optional.of(existente));

        service.provisionar("usuario@ejemplo.com", "ClaveOriginal1!", "UsuarioOriginal");
        service.provisionar(" USUARIO@Ejemplo.COM ", "ClaveNueva123!", " Nombre Nuevo ");

        verify(usuarioRepository, times(2)).findByEmail("usuario@ejemplo.com");
        verify(usuarioRepository, never()).save(any(Usuario.class));
        assertEquals("usuario@ejemplo.com", existente.getEmail());
        assertEquals("UsuarioOriginal", existente.getNombre());
        assertEquals(hashOriginal, existente.getPasswordHash());
        assertTrue(encoder.matches("ClaveOriginal1!", existente.getPasswordHash()));
    }

    private Usuario usuario(String email, String nombre, String hash) {
        Usuario usuario = new Usuario();
        usuario.setId(1L);
        usuario.setEmail(email);
        usuario.setNombre(nombre);
        usuario.setPasswordHash(hash);
        return usuario;
    }
}
