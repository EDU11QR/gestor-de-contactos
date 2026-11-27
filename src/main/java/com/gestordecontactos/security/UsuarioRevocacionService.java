package com.gestordecontactos.security;

import com.gestordecontactos.entity.Usuario;
import com.gestordecontactos.repository.SesionRevocacionJdbcRepository;
import com.gestordecontactos.repository.UsuarioRepository;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UsuarioRevocacionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(UsuarioRevocacionService.class);

    private final UsuarioRepository usuarioRepository;
    private final SesionRevocacionJdbcRepository sesionRepository;

    public UsuarioRevocacionService(UsuarioRepository usuarioRepository,
            SesionRevocacionJdbcRepository sesionRepository) {
        this.usuarioRepository = usuarioRepository;
        this.sesionRepository = sesionRepository;
    }

    @Transactional
    public void revocar(String email) {
        String emailNormalizado = UsuarioUserDetailsService.normalizar(email);
        if (emailNormalizado.isBlank()) {
            return;
        }
        UsuarioUserDetailsService.validarLongitudPrincipal(emailNormalizado);

        Optional<Usuario> usuario = usuarioRepository.findByEmailForUpdate(emailNormalizado);
        if (usuario.isEmpty()) {
            LOGGER.warn("Se intentó revocar un usuario inexistente");
            return;
        }

        Usuario entidad = usuario.get();
        if (entidad.isActivo()) {
            entidad.setActivo(false);
            usuarioRepository.save(entidad);
        }
        sesionRepository.eliminarPorPrincipalName(emailNormalizado);
        LOGGER.info("Revocación de usuario completada");
    }
}
