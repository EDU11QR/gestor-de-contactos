package com.gestordecontactos.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gestordecontactos.repository.UsuarioRepository;
import jakarta.servlet.http.Cookie;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
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
        "app.provisioning.nombre=",
        "app.revocar.email="
})
@ContextConfiguration(initializers = SesionJdbcConMysqlTests.VerificarBaseDeDatosDePrueba.class)
@Import(ConcurrenciaRevocacionLoginMysqlTests.ConfiguracionSincronizacion.class)
@AutoConfigureMockMvc
class ConcurrenciaRevocacionLoginMysqlTests {

    private static final String PASSWORD_PLANO = "Clave123!";
    private static final AtomicReference<String> CORREO_ACTUAL = new AtomicReference<>();
    private static final AtomicInteger LLAMADAS_BLOQUEO = new AtomicInteger();
    private static final AtomicReference<CountDownLatch> USUARIO_CARGADO = new AtomicReference<>(new CountDownLatch(1));
    private static final AtomicReference<CountDownLatch> CONTINUAR_LOGIN = new AtomicReference<>(new CountDownLatch(1));
    private static final AtomicReference<CountDownLatch> REVOCACION_EN_PUNTO_DE_BLOQUEO =
            new AtomicReference<>(new CountDownLatch(1));
    private static final AtomicReference<CountDownLatch> CONTINUAR_REVOCACION =
            new AtomicReference<>(new CountDownLatch(1));
    private static final AtomicReference<CountDownLatch> REVOCACION_LLAMANDO_BLOQUEO =
            new AtomicReference<>(new CountDownLatch(1));

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UsuarioRevocacionService usuarioRevocacionService;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    private String email;

    @BeforeEach
    void prepararUsuario() {
        email = "conc-" + UUID.randomUUID() + "@ejemplo.com";
        CORREO_ACTUAL.set(email);
        LLAMADAS_BLOQUEO.set(0);
        USUARIO_CARGADO.set(new CountDownLatch(1));
        CONTINUAR_LOGIN.set(new CountDownLatch(1));
        REVOCACION_EN_PUNTO_DE_BLOQUEO.set(new CountDownLatch(1));
        CONTINUAR_REVOCACION.set(new CountDownLatch(1));
        REVOCACION_LLAMANDO_BLOQUEO.set(new CountDownLatch(1));
        jdbcTemplate.update("INSERT INTO usuarios (email, password_hash, nombre, activo) VALUES (?, ?, ?, TRUE)",
                email, encoder.encode(PASSWORD_PLANO), "Usuario de prueba");
    }

    @AfterEach
    void limpiar() {
        CONTINUAR_LOGIN.get().countDown();
        CONTINUAR_REVOCACION.get().countDown();
        CORREO_ACTUAL.set("");
        if (email == null) {
            return;
        }
        jdbcTemplate.update("DELETE FROM SPRING_SESSION WHERE PRINCIPAL_NAME = ?", email);
        jdbcTemplate.update("DELETE FROM usuarios WHERE email = ?", email);
    }

    @Test
    void loginIniciadoAntesDeRevocacionNoDejaSesionUtilizable() throws Exception {
        CountDownLatch usuarioCargado = USUARIO_CARGADO.get();
        CountDownLatch continuarLogin = CONTINUAR_LOGIN.get();
        CountDownLatch revocacionEnPuntoDeBloqueo = REVOCACION_EN_PUNTO_DE_BLOQUEO.get();
        CountDownLatch continuarRevocacion = CONTINUAR_REVOCACION.get();
        CountDownLatch revocacionLlamandoBloqueo = REVOCACION_LLAMANDO_BLOQUEO.get();
        AtomicReference<Throwable> falloRevocacion = new AtomicReference<>();

        ExecutorService ejecutor = Executors.newSingleThreadExecutor();
        Thread hiloRevocacion = null;
        try {
            Future<MvcResult> autenticacion = ejecutor.submit(() -> mockMvc.perform(formLogin()
                    .user(email)
                    .password(PASSWORD_PLANO))
                    .andReturn());

            assertTrue(usuarioCargado.await(10, TimeUnit.SECONDS),
                    "La autenticación no cargó el usuario dentro del timeout");

            hiloRevocacion = new Thread(() -> {
                try {
                    usuarioRevocacionService.revocar(email);
                } catch (Throwable ex) {
                    falloRevocacion.set(ex);
                }
            }, "revocacion-usuario");
            hiloRevocacion.start();

            assertTrue(revocacionEnPuntoDeBloqueo.await(10, TimeUnit.SECONDS),
                    "La revocación no alcanzó la adquisición del bloqueo de la fila");
            assertFalse(autenticacion.isDone(),
                    "El login se reanudó antes de que la revocación alcanzara el bloqueo");
            assertTrue(hiloRevocacion.isAlive(),
                    "La revocación terminó antes de reanudar el login");
            assertNull(falloRevocacion.get());

            continuarRevocacion.countDown();
            assertTrue(revocacionLlamandoBloqueo.await(10, TimeUnit.SECONDS),
                    "La revocación no inició la consulta con bloqueo");
            assertFalse(autenticacion.isDone(),
                    "El login se reanudó antes de que la revocación intentara el bloqueo");

            continuarLogin.countDown();

            MvcResult resultado = autenticacion.get(10, TimeUnit.SECONDS);
            Cookie cookie = resultado.getResponse().getCookie("GESTOR_SESSION");
            assertNotNull(cookie);

            hiloRevocacion.join(10_000);
            assertFalse(hiloRevocacion.isAlive());
            assertNull(falloRevocacion.get());

            assertFalse(jdbcTemplate.queryForObject(
                    "SELECT activo FROM usuarios WHERE email = ?", Boolean.class, email));
            assertEquals(0, contarSesiones(email));

            mockMvc.perform(get("/contactos").cookie(cookie))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrlPattern("**/login"));
        } finally {
            continuarRevocacion.countDown();
            continuarLogin.countDown();
            if (hiloRevocacion != null) {
                hiloRevocacion.join(10_000);
            }
            ejecutor.shutdownNow();
            ejecutor.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    private int contarSesiones(String correo) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM SPRING_SESSION WHERE PRINCIPAL_NAME = ?", Integer.class, correo);
    }

    private static boolean esCorreoActual(Object[] args) {
        return args != null && args.length == 1 && CORREO_ACTUAL.get().equals(args[0]);
    }

    private static Object invocar(UsuarioRepository repositorio, Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(repositorio, args);
        } catch (InvocationTargetException ex) {
            throw ex.getCause();
        }
    }

    @TestConfiguration
    static class ConfiguracionSincronizacion {

        @Bean
        static BeanPostProcessor sincronizacionUsuarioRepository() {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String beanName) {
                    if (!(bean instanceof UsuarioRepository repositorio)) {
                        return bean;
                    }
                    return Proxy.newProxyInstance(
                            UsuarioRepository.class.getClassLoader(),
                            new Class<?>[]{UsuarioRepository.class},
                            (proxy, method, args) -> {
                                if (method.getName().equals("findByEmailForUpdate") && esCorreoActual(args)
                                        && LLAMADAS_BLOQUEO.incrementAndGet() == 2) {
                                    CountDownLatch enPunto = REVOCACION_EN_PUNTO_DE_BLOQUEO.get();
                                    CountDownLatch continuar = CONTINUAR_REVOCACION.get();
                                    CountDownLatch llamando = REVOCACION_LLAMANDO_BLOQUEO.get();
                                    enPunto.countDown();
                                    if (!continuar.await(10, TimeUnit.SECONDS)) {
                                        throw new IllegalStateException("Timeout esperando reanudar la revocación");
                                    }
                                    llamando.countDown();
                                }
                                Object resultado = invocar(repositorio, method, args);
                                if (method.getName().equals("findByEmail") && esCorreoActual(args)) {
                                    CountDownLatch cargado = USUARIO_CARGADO.get();
                                    CountDownLatch continuar = CONTINUAR_LOGIN.get();
                                    cargado.countDown();
                                    if (!continuar.await(10, TimeUnit.SECONDS)) {
                                        throw new IllegalStateException("Timeout esperando reanudar la autenticación");
                                    }
                                }
                                return resultado;
                            });
                }
            };
        }
    }
}
