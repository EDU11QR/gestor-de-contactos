package com.gestordecontactos.security;

import com.gestordecontactos.entity.Usuario;
import com.gestordecontactos.repository.UsuarioRepository;
import java.util.Locale;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UsuarioUserDetailsService implements UserDetailsService {

    private static final int MAX_LONGITUD_PRINCIPAL = 100;

    private final UsuarioRepository usuarioRepository;

    public UsuarioUserDetailsService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    public static String normalizar(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    public static void validarLongitudPrincipal(String email) {
        if (email.length() > MAX_LONGITUD_PRINCIPAL) {
            throw new IllegalArgumentException("El email del usuario no puede superar 100 caracteres");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        String email = normalizar(username);
        if (email.isBlank() || email.length() > MAX_LONGITUD_PRINCIPAL) {
            throw new UsernameNotFoundException("Credenciales inválidas");
        }
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Credenciales inválidas"));
        if (!usuario.isActivo()) {
            throw new DisabledException("Credenciales inválidas");
        }
        return UsuarioPrincipal.from(usuario);
    }
}
