package com.gestordecontactos.security;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

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
        "app.provisioning.nombre="
})
@ContextConfiguration(initializers = SesionJdbcConMysqlTests.VerificarBaseDeDatosDePrueba.class)
@AutoConfigureMockMvc
class SesionJdbcConMysqlTests {

    private static final String PASSWORD_PLANO = "Clave123!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    private String email;
    private String hash;

    @BeforeEach
    void prepararUsuario() {
        email = "sesion-" + UUID.randomUUID() + "@ejemplo.com";
        hash = encoder.encode(PASSWORD_PLANO);
        jdbcTemplate.update("INSERT INTO usuarios (email, password_hash, nombre) VALUES (?, ?, ?)",
                email, hash, "Usuario de prueba");
    }

    @AfterEach
    void limpiar() {
        jdbcTemplate.update("DELETE FROM SPRING_SESSION_ATTRIBUTES WHERE SESSION_PRIMARY_ID IN "
                + "(SELECT PRIMARY_ID FROM SPRING_SESSION WHERE PRINCIPAL_NAME = ?)", email);
        jdbcTemplate.update("DELETE FROM SPRING_SESSION WHERE PRINCIPAL_NAME = ?", email);
        jdbcTemplate.update("DELETE FROM usuarios WHERE email = ?", email);
    }

    @Test
    void sesionPersistidaNoConservaCredencialesYSeReutiliza() throws Exception {
        MvcResult resultadoLogin = mockMvc.perform(formLogin()
                        .user(email)
                        .password(PASSWORD_PLANO))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andReturn();

        Cookie cookieSesion = resultadoLogin.getResponse().getCookie("GESTOR_SESSION");
        assertNotNull(cookieSesion);
        String idSesion = new String(Base64.getUrlDecoder().decode(cookieSesion.getValue()), StandardCharsets.UTF_8);

        assertEquals(email, jdbcTemplate.queryForObject(
                "SELECT PRINCIPAL_NAME FROM SPRING_SESSION WHERE SESSION_ID = ?", String.class, idSesion));

        byte[] bytes = jdbcTemplate.queryForObject(
                "SELECT a.ATTRIBUTE_BYTES FROM SPRING_SESSION_ATTRIBUTES a "
                        + "JOIN SPRING_SESSION s ON s.PRIMARY_ID = a.SESSION_PRIMARY_ID "
                        + "WHERE s.SESSION_ID = ? AND a.ATTRIBUTE_NAME = 'SPRING_SECURITY_CONTEXT'",
                byte[].class, idSesion);
        assertNotNull(bytes);

        SecurityContext contexto;
        try (ObjectInputStream entrada = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            contexto = (SecurityContext) entrada.readObject();
        }

        Authentication autenticacion = contexto.getAuthentication();
        assertNotNull(autenticacion);
        UsuarioPrincipal principal = assertInstanceOf(UsuarioPrincipal.class, autenticacion.getPrincipal());
        assertNull(principal.getPassword());
        assertNull(autenticacion.getCredentials());

        String contenido = new String(bytes, StandardCharsets.ISO_8859_1);
        assertFalse(contenido.contains(hash));
        assertFalse(contenido.contains(PASSWORD_PLANO));

        mockMvc.perform(get("/").cookie(cookieSesion))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Cerrar sesión")));

        mockMvc.perform(post("/logout").with(csrf()).cookie(cookieSesion))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?logout"));

        Integer sesionesRestantes = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM SPRING_SESSION WHERE SESSION_ID = ?", Integer.class, idSesion);
        assertEquals(0, sesionesRestantes);
    }

    static class VerificarBaseDeDatosDePrueba
            implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        @Override
        public void initialize(ConfigurableApplicationContext applicationContext) {
            String url = applicationContext.getEnvironment().getProperty("spring.datasource.url");
            assertNotNull(url, "SPRING_DATASOURCE_URL es obligatoria");
            String uri = url.startsWith("jdbc:") ? url.substring("jdbc:".length()) : url;
            String ruta = URI.create(uri).getPath();
            assertNotNull(ruta, "SPRING_DATASOURCE_URL debe incluir el nombre de la base de datos");
            String nombreBase = ruta.startsWith("/") ? ruta.substring(1) : ruta;
            int finNombreBase = nombreBase.indexOf('/');
            if (finNombreBase >= 0) {
                nombreBase = nombreBase.substring(0, finNombreBase);
            }
            assertEquals("gestor_contactos_test", nombreBase,
                    "La prueba solo puede ejecutarse contra gestor_contactos_test");
            assertNotNull(applicationContext.getEnvironment().getProperty("spring.datasource.username"));
            assertNotNull(applicationContext.getEnvironment().getProperty("spring.datasource.password"));
        }
    }
}
