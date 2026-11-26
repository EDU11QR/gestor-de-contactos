package com.gestordecontactos.security;

import com.gestordecontactos.repository.UsuarioRepository;
import java.util.Locale;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UsuarioUserDetailsService implements UserDetailsService {

    private final UsuarioRepository usuarioRepository;

    public UsuarioUserDetailsService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    public static String normalizar(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        String email = normalizar(username);
        if (email.isBlank()) {
            throw new UsernameNotFoundException("Credenciales inválidas");
        }
        return usuarioRepository.findByEmail(email)
                .map(UsuarioPrincipal::from)
                .orElseThrow(() -> new UsernameNotFoundException("Credenciales inválidas"));
    }
}
