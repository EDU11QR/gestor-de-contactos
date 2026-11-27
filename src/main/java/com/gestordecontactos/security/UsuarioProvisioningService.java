package com.gestordecontactos.security;

import com.gestordecontactos.entity.Usuario;
import com.gestordecontactos.repository.UsuarioRepository;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UsuarioProvisioningService {

    private static final int LONGITUD_MINIMA_PASSWORD = 12;

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
        if (emailNormalizado.isBlank() || nombreNormalizado.isBlank()) {
            return;
        }
        UsuarioUserDetailsService.validarLongitudPrincipal(emailNormalizado);
        if (password == null || password.isBlank()) {
            return;
        }
        if (!passwordValida(password, emailNormalizado, nombreNormalizado)) {
            throw new IllegalArgumentException("La contraseña de aprovisionamiento no cumple la política de seguridad");
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

    private boolean passwordValida(String password, String email, String nombre) {
        return password.length() >= LONGITUD_MINIMA_PASSWORD
                && password.equals(password.trim())
                && !password.matches(".*\\s.*")
                && !password.equalsIgnoreCase(email)
                && !password.equalsIgnoreCase(nombre);
    }
}
