package com.gestordecontactos.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.filter.OncePerRequestFilter;

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
@Import(RollbackFiltroLoginMysqlTests.ConfiguracionFallo.class)
@AutoConfigureMockMvc
class RollbackFiltroLoginMysqlTests {

    private static final String PASSWORD_PLANO = "Clave123!";
    private static final AtomicBoolean FALLAR_LOGIN = new AtomicBoolean(false);
    private static final AtomicReference<String> CORREO_ACTUAL = new AtomicReference<>();
    private static final AtomicReference<String> ID_SESION_RESPUESTA = new AtomicReference<>();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    private String email;
    private String hashInicial;

    @BeforeEach
    void prepararUsuario() {
        FALLAR_LOGIN.set(false);
        CORREO_ACTUAL.set(null);
        ID_SESION_RESPUESTA.set(null);
        email = "rollback-login-" + UUID.randomUUID() + "@ejemplo.com";
        hashInicial = encoder.encode(PASSWORD_PLANO);
        jdbcTemplate.update("INSERT INTO usuarios (email, password_hash, nombre, activo) VALUES (?, ?, ?, TRUE)",
                email, hashInicial, "Usuario de prueba");
    }

    @AfterEach
    void limpiar() {
        FALLAR_LOGIN.set(false);
        CORREO_ACTUAL.set(null);
        ID_SESION_RESPUESTA.set(null);
        if (email == null) {
            return;
        }
        jdbcTemplate.update("DELETE FROM SPRING_SESSION_ATTRIBUTES WHERE SESSION_PRIMARY_ID IN "
                + "(SELECT PRIMARY_ID FROM SPRING_SESSION WHERE PRINCIPAL_NAME = ?)", email);
        jdbcTemplate.update("DELETE FROM SPRING_SESSION WHERE PRINCIPAL_NAME = ?", email);
        jdbcTemplate.update("DELETE FROM usuarios WHERE email = ?", email);
    }

    @Test
    void excepcionAguasAbajoRevierteLaSesionYPermiteUnLoginPosterior() throws Exception {
        FALLAR_LOGIN.set(true);
        CORREO_ACTUAL.set(email);

        assertThrows(Exception.class,
                () -> mockMvc.perform(formLogin().user(email).password(PASSWORD_PLANO)));

        assertEquals(0, contarSesiones(email));
        assertEquals(0, contarAtributos(email));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM usuarios WHERE email = ?", Integer.class, email));
        assertTrue(jdbcTemplate.queryForObject(
                "SELECT activo FROM usuarios WHERE email = ?", Boolean.class, email));
        assertEquals(hashInicial, jdbcTemplate.queryForObject(
                "SELECT password_hash FROM usuarios WHERE email = ?", String.class, email));
        assertEquals("Usuario de prueba", jdbcTemplate.queryForObject(
                "SELECT nombre FROM usuarios WHERE email = ?", String.class, email));

        FALLAR_LOGIN.set(false);

        Cookie cookie = mockMvc.perform(formLogin().user(email).password(PASSWORD_PLANO))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andReturn().getResponse().getCookie("GESTOR_SESSION");
        assertNotNull(cookie);
        assertEquals(1, contarSesiones(email));

        mockMvc.perform(get("/contactos").cookie(cookie))
                .andExpect(status().isOk());
    }

    @Test
    void rollbackConSesionPreviaConservaLaSesionAntiguaYNoPersisteLaNueva() throws Exception {
        Cookie cookiePrevia = mockMvc.perform(formLogin().user(email).password(PASSWORD_PLANO))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andReturn().getResponse().getCookie("GESTOR_SESSION");
        assertNotNull(cookiePrevia);
        String idSesionPrevio = idSesionDe(cookiePrevia);
        String idPrimarioPrevio = jdbcTemplate.queryForObject(
                "SELECT PRIMARY_ID FROM SPRING_SESSION WHERE SESSION_ID = ?", String.class, idSesionPrevio);
        assertNotNull(idPrimarioPrevio);

        FALLAR_LOGIN.set(true);
        CORREO_ACTUAL.set(email);

        assertThrows(Exception.class, () -> mockMvc.perform(post("/login")
                .with(csrf())
                .param("username", email)
                .param("password", PASSWORD_PLANO)
                .cookie(cookiePrevia)));

        assertEquals("Usuario de prueba", jdbcTemplate.queryForObject(
                "SELECT nombre FROM usuarios WHERE email = ?", String.class, email));
        assertTrue(jdbcTemplate.queryForObject(
                "SELECT activo FROM usuarios WHERE email = ?", Boolean.class, email));
        assertEquals(hashInicial, jdbcTemplate.queryForObject(
                "SELECT password_hash FROM usuarios WHERE email = ?", String.class, email));

        assertEquals(1, contarSesiones(email));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM SPRING_SESSION WHERE PRIMARY_ID = ?", Integer.class, idPrimarioPrevio));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM SPRING_SESSION_ATTRIBUTES WHERE SESSION_PRIMARY_ID = ?",
                Integer.class, idPrimarioPrevio));

        String idSesionRespuesta = ID_SESION_RESPUESTA.get();
        assertNotNull(idSesionRespuesta);
        assertNotEquals(idSesionPrevio, idSesionRespuesta);
        assertEquals(0, contarSesionesPorId(idSesionRespuesta));

        mockMvc.perform(get("/").cookie(cookiePrevia))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Cerrar sesión")));

        FALLAR_LOGIN.set(false);

        Cookie cookiePosterior = mockMvc.perform(formLogin().user(email).password(PASSWORD_PLANO))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andReturn().getResponse().getCookie("GESTOR_SESSION");
        assertNotNull(cookiePosterior);
        assertEquals(1, contarSesionesPorId(idSesionDe(cookiePosterior)));
        mockMvc.perform(get("/").cookie(cookiePosterior))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Cerrar sesión")));
    }

    private static String idSesionDe(Cookie cookie) {
        return new String(Base64.getDecoder().decode(cookie.getValue()), StandardCharsets.UTF_8);
    }

    private static String idSesionDeCabecera(String cabecera) {
        String prefijo = "GESTOR_SESSION=";
        if (!cabecera.startsWith(prefijo)) {
            return null;
        }
        int fin = cabecera.indexOf(';', prefijo.length());
        String valor = fin >= 0 ? cabecera.substring(prefijo.length(), fin)
                : cabecera.substring(prefijo.length());
        try {
            return new String(Base64.getDecoder().decode(valor), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private int contarSesiones(String correo) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM SPRING_SESSION WHERE PRINCIPAL_NAME = ?", Integer.class, correo);
    }

    private int contarSesionesPorId(String idSesion) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM SPRING_SESSION WHERE SESSION_ID = ?", Integer.class, idSesion);
    }

    private int contarAtributos(String correo) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM SPRING_SESSION_ATTRIBUTES a "
                        + "JOIN SPRING_SESSION s ON s.PRIMARY_ID = a.SESSION_PRIMARY_ID "
                        + "WHERE s.PRINCIPAL_NAME = ?", Integer.class, correo);
    }

    @TestConfiguration
    static class ConfiguracionFallo {

        @Bean
        FilterRegistrationBean<AutenticacionRevocacionFilter> registroAutenticacionRevocacion(
                AutenticacionRevocacionFilter filtro) {
            FilterRegistrationBean<AutenticacionRevocacionFilter> registro =
                    new FilterRegistrationBean<>(filtro);
            registro.setOrder(Ordered.HIGHEST_PRECEDENCE);
            registro.addUrlPatterns("/*");
            return registro;
        }

        @Bean
        FilterRegistrationBean<FiltroFallaAguasAbajo> filtroFallaAguasAbajo(JdbcTemplate jdbcTemplate) {
            FilterRegistrationBean<FiltroFallaAguasAbajo> registro =
                    new FilterRegistrationBean<>(new FiltroFallaAguasAbajo(jdbcTemplate));
            registro.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
            registro.addUrlPatterns("/*");
            return registro;
        }
    }

    static class FiltroFallaAguasAbajo extends OncePerRequestFilter {

        private final JdbcTemplate jdbcTemplate;

        FiltroFallaAguasAbajo(JdbcTemplate jdbcTemplate) {
            this.jdbcTemplate = jdbcTemplate;
        }

        @Override
        protected boolean shouldNotFilter(HttpServletRequest request) {
            return !FALLAR_LOGIN.get()
                    || !"POST".equalsIgnoreCase(request.getMethod())
                    || !"/login".equals(request.getRequestURI());
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                FilterChain chain) throws ServletException, IOException {
            chain.doFilter(request, response);
            response.getHeaders("Set-Cookie").stream()
                    .map(RollbackFiltroLoginMysqlTests::idSesionDeCabecera)
                    .filter(id -> id != null && !id.isBlank())
                    .forEach(ID_SESION_RESPUESTA::set);
            jdbcTemplate.update("UPDATE usuarios SET nombre = 'ROLLBACK_TEMP' WHERE email = ?",
                    CORREO_ACTUAL.get());
            throw new IllegalStateException("Fallo forzado después de autenticar y persistir la sesión");
        }
    }
}
