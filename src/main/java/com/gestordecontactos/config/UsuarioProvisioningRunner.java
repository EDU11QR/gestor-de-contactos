package com.gestordecontactos.config;

import com.gestordecontactos.security.UsuarioProvisioningService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(1)
public class UsuarioProvisioningRunner implements ApplicationRunner {

    private final UsuarioProvisioningService usuarioProvisioningService;
    private final String email;
    private final String password;
    private final String nombre;

    public UsuarioProvisioningRunner(UsuarioProvisioningService usuarioProvisioningService,
            @Value("${app.provisioning.email:}") String email,
            @Value("${app.provisioning.password:}") String password,
            @Value("${app.provisioning.nombre:}") String nombre) {
        this.usuarioProvisioningService = usuarioProvisioningService;
        this.email = email;
        this.password = password;
        this.nombre = nombre;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            return;
        }
        usuarioProvisioningService.provisionar(email, password, nombre);
    }
}
