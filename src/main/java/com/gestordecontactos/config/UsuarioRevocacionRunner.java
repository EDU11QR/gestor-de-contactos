package com.gestordecontactos.config;

import com.gestordecontactos.security.UsuarioRevocacionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(2)
public class UsuarioRevocacionRunner implements ApplicationRunner {

    private final UsuarioRevocacionService usuarioRevocacionService;
    private final String email;

    public UsuarioRevocacionRunner(UsuarioRevocacionService usuarioRevocacionService,
            @Value("${app.revocar.email:}") String email) {
        this.usuarioRevocacionService = usuarioRevocacionService;
        this.email = email;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (email == null || email.isBlank()) {
            return;
        }
        usuarioRevocacionService.revocar(email);
    }
}
