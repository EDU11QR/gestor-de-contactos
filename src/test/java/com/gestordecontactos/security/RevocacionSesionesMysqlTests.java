package com.gestordecontactos.security;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "spring.datasource.url=${SPRING_DATASOURCE_URL}",
        "spring.datasource.username=${SPRING_DATASOURCE_USERNAME}",
        "spring.datasource.password=${SPRING_DATASOURCE_PASSWORD}",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.session.store-type=jdbc",
        "spring.session.jdbc.table-name=SPRING_SESSION",
        "spring.session.jdbc.initialize-schema=never",
        "app.provisioning.email=",
        "app.provisioning.password=",
        "app.provisioning.nombre=",
        "app.revocar.email="
})
@ContextConfiguration(initializers = SesionJdbcConMysqlTests.VerificarBaseDeDatosDePrueba.class)
@AutoConfigureMockMvc
class RevocacionSesionesMysqlTests {

    private static final String PASSWORD_PLANO = "Clave123!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UsuarioRevocacionService usuarioRevocacionService;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    private String email;
    private String otroEmail;
    private Cookie primeraCookie;
    private Cookie segundaCookie;
    private Cookie cookieOtroUsuario;
    private List<String> idsSesionesRevocadas;

    @BeforeEach
    void prepararUsuariosYSesiones() throws Exception {
        email = "rev-" + UUID.randomUUID() + "@ejemplo.com";
        otroEmail = "otro-" + UUID.randomUUID() + "@ejemplo.com";
        insertarUsuario(email);
        insertarUsuario(otroEmail);

        primeraCookie = iniciarSesion(email);
        segundaCookie = iniciarSesion(email);
        cookieOtroUsuario = iniciarSesion(otroEmail);
        idsSesionesRevocadas = jdbcTemplate.queryForList(
                "SELECT PRIMARY_ID FROM SPRING_SESSION WHERE PRINCIPAL_NAME = ?", String.class, email);

        assertEquals(2, idsSesionesRevocadas.size());
        assertTrue(contarAtributos(idsSesionesRevocadas) > 0);
        assertTrue(jdbcTemplate.queryForObject(
                "SELECT activo FROM usuarios WHERE email = ?", Boolean.class, email));
    }

    @AfterEach
    void limpiar() {
        if (email == null || otroEmail == null) {
            return;
        }
        jdbcTemplate.update("DELETE FROM SPRING_SESSION WHERE PRINCIPAL_NAME IN (?, ?)", email, otroEmail);
        jdbcTemplate.update("DELETE FROM usuarios WHERE email IN (?, ?)", email, otroEmail);
    }

    @Test
    void revocarEliminaSesionesDelUsuarioConservaOtrasYBloqueaNuevoLogin() throws Exception {
        usuarioRevocacionService.revocar("  " + email.toUpperCase(Locale.ROOT) + "  ");

        assertEquals(0, contarSesiones(email));
        assertEquals(1, contarSesiones(otroEmail));
        assertEquals(0, contarAtributos(idsSesionesRevocadas));
        assertFalse(jdbcTemplate.queryForObject(
                "SELECT activo FROM usuarios WHERE email = ?", Boolean.class, email));

        mockMvc.perform(get("/contactos").cookie(primeraCookie))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
        mockMvc.perform(get("/contactos").cookie(segundaCookie))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
        mockMvc.perform(get("/contactos").cookie(cookieOtroUsuario))
                .andExpect(status().isOk());
        mockMvc.perform(get("/").cookie(cookieOtroUsuario))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Cerrar sesión")));

        mockMvc.perform(formLogin().user(email).password(PASSWORD_PLANO))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error"));

        usuarioRevocacionService.revocar(email);
        assertEquals(0, contarSesiones(email));
    }

    @Test
    void revocarUsuarioInexistenteNoAfectaSesiones() {
        usuarioRevocacionService.revocar("ausente-" + UUID.randomUUID() + "@ejemplo.com");

        assertEquals(2, contarSesiones(email));
        assertEquals(1, contarSesiones(otroEmail));
    }

    @Test
    void loginConCorreoSuperiorACienCaracteresFallaSinCrearSesion() throws Exception {
        String correoLargo = "a".repeat(101) + "@ejemplo.com";

        mockMvc.perform(formLogin().user(correoLargo).password(PASSWORD_PLANO))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error"));

        assertEquals(0, contarSesiones(correoLargo));
    }

    private void insertarUsuario(String correo) {
        jdbcTemplate.update("INSERT INTO usuarios (email, password_hash, nombre, activo) VALUES (?, ?, ?, TRUE)",
                correo, encoder.encode(PASSWORD_PLANO), "Usuario de prueba");
    }

    private Cookie iniciarSesion(String correo) throws Exception {
        Cookie cookie = mockMvc.perform(formLogin().user(correo).password(PASSWORD_PLANO))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andReturn()
                .getResponse()
                .getCookie("GESTOR_SESSION");
        assertNotNull(cookie);
        return cookie;
    }

    private int contarSesiones(String correo) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM SPRING_SESSION WHERE PRINCIPAL_NAME = ?", Integer.class, correo);
    }

    private int contarAtributos(List<String> ids) {
        if (ids.isEmpty()) {
            return 0;
        }
        String marcadores = String.join(",", Collections.nCopies(ids.size(), "?"));
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM SPRING_SESSION_ATTRIBUTES WHERE SESSION_PRIMARY_ID IN (" + marcadores + ")",
                Integer.class, ids.toArray());
    }
}
