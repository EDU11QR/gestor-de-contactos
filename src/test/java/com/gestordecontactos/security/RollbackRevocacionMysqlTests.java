package com.gestordecontactos.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ContextConfiguration;

@SpringBootTest(properties = {
        "spring.datasource.url=${SPRING_DATASOURCE_URL}",
        "spring.datasource.username=${SPRING_DATASOURCE_USERNAME}",
        "spring.datasource.password=${SPRING_DATASOURCE_PASSWORD}",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.session.store-type=jdbc",
        "spring.session.jdbc.table-name=SPRING_SESSION_INEXISTENTE",
        "spring.session.jdbc.initialize-schema=never",
        "app.provisioning.email=",
        "app.provisioning.password=",
        "app.provisioning.nombre=",
        "app.revocar.email="
})
@ContextConfiguration(initializers = SesionJdbcConMysqlTests.VerificarBaseDeDatosDePrueba.class)
class RollbackRevocacionMysqlTests {

    @Autowired
    private UsuarioRevocacionService usuarioRevocacionService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    private String email;

    @BeforeEach
    void prepararUsuario() {
        email = "rollback-" + UUID.randomUUID() + "@ejemplo.com";
        jdbcTemplate.update("INSERT INTO usuarios (email, password_hash, nombre, activo) VALUES (?, ?, ?, TRUE)",
                email, encoder.encode("Clave123!"), "Usuario de prueba");
    }

    @AfterEach
    void limpiar() {
        jdbcTemplate.update("DELETE FROM SPRING_SESSION WHERE PRINCIPAL_NAME = ?", email);
        jdbcTemplate.update("DELETE FROM usuarios WHERE email = ?", email);
    }

    @Test
    void falloJdbcAlEliminarSesionesRevierteCambioDeActivo() {
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.TABLES "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'SPRING_SESSION_INEXISTENTE'",
                Integer.class));

        DataAccessException ex = assertThrows(DataAccessException.class,
                () -> usuarioRevocacionService.revocar(" " + email.toUpperCase(Locale.ROOT) + " "));
        assertNotNull(ex.getMessage());

        Boolean activo = jdbcTemplate.queryForObject(
                "SELECT activo FROM usuarios WHERE email = ?", Boolean.class, email);
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM usuarios WHERE email = ?", Integer.class, email));
        assertTrue(activo);
    }
}
