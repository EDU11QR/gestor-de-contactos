package com.gestordecontactos.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gestordecontactos.entity.Usuario;
import com.gestordecontactos.repository.UsuarioRepository;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class AutenticacionTests {

    private final UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Test
    void autenticaConCorreoNormalizadoYBorraCredencialesDelPrincipal() {
        when(usuarioRepository.findByEmail("usuario@ejemplo.com"))
                .thenReturn(Optional.of(usuario(encoder.encode("Clave123!"))));

        Authentication resultado = authenticationManager().authenticate(
                new UsernamePasswordAuthenticationToken(" USUARIO@Ejemplo.COM ", "Clave123!"));

        assertEquals("usuario@ejemplo.com", resultado.getName());
        UsuarioPrincipal principal = assertInstanceOf(UsuarioPrincipal.class, resultado.getPrincipal());
        assertEquals("usuario@ejemplo.com", principal.getUsername());
        assertNull(principal.getPassword());
        assertNull(resultado.getCredentials());
        verify(usuarioRepository).findByEmail("usuario@ejemplo.com");
    }

    @Test
    void rechazaContrasenaIncorrecta() {
        when(usuarioRepository.findByEmail("usuario@ejemplo.com"))
                .thenReturn(Optional.of(usuario(encoder.encode("Clave123!"))));

        assertThrows(BadCredentialsException.class, () -> authenticationManager().authenticate(
                new UsernamePasswordAuthenticationToken("usuario@ejemplo.com", "Incorrecta")));
    }

    @Test
    void rechazaCorreoInexistente() {
        when(usuarioRepository.findByEmail("ausente@ejemplo.com")).thenReturn(Optional.empty());

        assertThrows(BadCredentialsException.class, () -> authenticationManager().authenticate(
                new UsernamePasswordAuthenticationToken("ausente@ejemplo.com", "Clave123!")));
    }

    @Test
    void principalEsSerializableConAutoridadFijaYBorraSusCredenciales() {
        UsuarioPrincipal principal = UsuarioPrincipal.from(usuario(encoder.encode("Clave123!")));

        assertInstanceOf(Serializable.class, principal);
        assertInstanceOf(CredentialsContainer.class, principal);
        assertEquals("ROLE_USER", principal.getAuthorities().iterator().next().getAuthority());

        principal.eraseCredentials();

        assertNull(principal.getPassword());
    }

    @Test
    void hashNoQuedaEnAtributoDeSesionSerializado() throws IOException {
        String hash = encoder.encode("Clave123!");
        when(usuarioRepository.findByEmail("usuario@ejemplo.com")).thenReturn(Optional.of(usuario(hash)));

        Authentication resultado = authenticationManager().authenticate(
                new UsernamePasswordAuthenticationToken("usuario@ejemplo.com", "Clave123!"));

        byte[] serializado = serializar(new SecurityContextImpl(resultado));
        String contenido = new String(serializado, StandardCharsets.ISO_8859_1);

        assertFalse(contenido.contains(hash));
        assertFalse(contenido.contains("Clave123!"));
    }

    private ProviderManager authenticationManager() {
        ErasingDaoAuthenticationProvider provider =
                new ErasingDaoAuthenticationProvider(new UsuarioUserDetailsService(usuarioRepository));
        provider.setPasswordEncoder(encoder);
        return new ProviderManager(provider);
    }

    private Usuario usuario(String hash) {
        Usuario usuario = new Usuario();
        usuario.setId(1L);
        usuario.setEmail("usuario@ejemplo.com");
        usuario.setNombre("Usuario");
        usuario.setPasswordHash(hash);
        return usuario;
    }

    private byte[] serializar(Object objeto) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream salida = new ObjectOutputStream(bytes)) {
            salida.writeObject(objeto);
        }
        return bytes.toByteArray();
    }
}
