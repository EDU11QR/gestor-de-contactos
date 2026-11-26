package com.gestordecontactos.security;

import com.gestordecontactos.entity.Usuario;
import com.gestordecontactos.repository.UsuarioRepository;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UsuarioProvisioningService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public UsuarioProvisioningService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public void provisionar(String email, String password, String nombre) {
        String emailNormalizado = UsuarioUserDetailsService.normalizar(email);
        String nombreNormalizado = nombre == null ? "" : nombre.trim();
        if (emailNormalizado.isBlank() || password == null || password.isBlank() || nombreNormalizado.isBlank()) {
            return;
        }

        Optional<Usuario> existente = usuarioRepository.findByEmail(emailNormalizado);
        if (existente.isPresent()) {
            return;
        }

        Usuario usuario = new Usuario();
        usuario.setEmail(emailNormalizado);
        usuario.setPasswordHash(passwordEncoder.encode(password));
        usuario.setNombre(nombreNormalizado);
        usuarioRepository.save(usuario);
    }
}
