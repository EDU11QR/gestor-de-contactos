package com.gestordecontactos.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gestordecontactos.controller.ContactoRegistroTests;
import com.gestordecontactos.exception.ConfirmacionInvalidaException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = {
        "spring.datasource.url=${SPRING_DATASOURCE_URL}",
        "spring.datasource.username=${SPRING_DATASOURCE_USERNAME}",
        "spring.datasource.password=${SPRING_DATASOURCE_PASSWORD}",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.session.store-type=jdbc",
        "spring.session.jdbc.table-name=SPRING_SESSION",
        "spring.session.jdbc.initialize-schema=never",
        "app.fotos.dir=${java.io.tmpdir}/gestor-contactos-test-fotos",
        "app.provisioning.email=",
        "app.provisioning.password=",
        "app.provisioning.nombre="
})
@ContextConfiguration(initializers = ContactoRegistroTests.VerificarBaseDeDatosDePrueba.class)
class ConfirmacionEliminacionMysqlTests {

    private static final Long ID_CONTACTO = 1L;
    private static final int SEGUNDOS_ESPERA = 20;

    @Autowired
    private ConfirmacionEliminacionService confirmacionEliminacionService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final List<String> sesiones = new CopyOnWriteArrayList<>();

    @AfterEach
    void limpiar() {
        for (String sesion : sesiones) {
            jdbcTemplate.update("DELETE FROM confirmaciones_eliminacion WHERE session_id = ?", sesion);
        }
    }

    @Test
    void consumoConcurrenteConsumaSoloUnaSolicitud() throws Exception {
        String sesion = nuevaSesion();
        String token = confirmacionEliminacionService.generar(ID_CONTACTO, sesion);
        ExecutorService ejecutor = Executors.newFixedThreadPool(2);
        CountDownLatch inicio = new CountDownLatch(1);
        Callable<Boolean> consumo = () -> {
            TransactionTemplate transaccion = new TransactionTemplate(transactionManager);
            inicio.await();
            return transaccion.execute(estado -> {
                try {
                    confirmacionEliminacionService.consumir(ID_CONTACTO, sesion, token);
                    return true;
                } catch (ConfirmacionInvalidaException ex) {
                    return false;
                }
            });
        };

        try {
            Future<Boolean> primero = ejecutor.submit(consumo);
            Future<Boolean> segundo = ejecutor.submit(consumo);
            inicio.countDown();

            boolean resultadoPrimero = primero.get(SEGUNDOS_ESPERA, TimeUnit.SECONDS);
            boolean resultadoSegundo = segundo.get(SEGUNDOS_ESPERA, TimeUnit.SECONDS);

            assertTrue(resultadoPrimero ^ resultadoSegundo,
                    "Solo una de las dos transacciones debe consumir el token");
        } finally {
            ejecutor.shutdownNow();
        }

        assertEquals(1, contarFilas(sesion));
        assertEquals(1, contarConsumidas(sesion, hash(token)));
    }

    @Test
    void consumoRequiresNewConfirmaAunqueLaTransaccionExternaHagaRollback() {
        String sesion = nuevaSesion();
        String token = confirmacionEliminacionService.generar(ID_CONTACTO, sesion);

        assertTrue(AopUtils.isAopProxy(confirmacionEliminacionService),
                "El servicio de confirmación debe invocarse mediante el proxy transaccional");

        TransactionTemplate transaccionExterna = new TransactionTemplate(transactionManager);
        transaccionExterna.executeWithoutResult(estado -> {
            confirmacionEliminacionService.consumir(ID_CONTACTO, sesion, token);
            estado.setRollbackOnly();
        });

        assertEquals(1, contarConsumidas(sesion, hash(token)));
    }

    @Test
    void tokenExpiradoNoSeConsume() {
        String sesion = nuevaSesion();
        String token = tokenNuevo();
        insertarConfirmacionExpirada(sesion, hash(token));

        assertThrows(ConfirmacionInvalidaException.class,
                () -> confirmacionEliminacionService.consumir(ID_CONTACTO, sesion, token));

        assertEquals(0, contarConsumidas(sesion, hash(token)));
    }

    @Test
    void tokenIncorrectoNoSeConsume() {
        String sesion = nuevaSesion();
        String token = confirmacionEliminacionService.generar(ID_CONTACTO, sesion);
        String tokenIncorrecto = "A".repeat(43);

        assertNotEquals(token, tokenIncorrecto);
        assertThrows(ConfirmacionInvalidaException.class,
                () -> confirmacionEliminacionService.consumir(ID_CONTACTO, sesion, tokenIncorrecto));

        assertEquals(0, contarConsumidas(sesion, hash(token)));
    }

    @Test
    void tokenYaConsumidoNoPuedeConsumirseOtraVez() {
        String sesion = nuevaSesion();
        String token = confirmacionEliminacionService.generar(ID_CONTACTO, sesion);

        confirmacionEliminacionService.consumir(ID_CONTACTO, sesion, token);

        assertThrows(ConfirmacionInvalidaException.class,
                () -> confirmacionEliminacionService.consumir(ID_CONTACTO, sesion, token));
        assertEquals(1, contarConsumidas(sesion, hash(token)));
    }

    @Test
    void nuevaConfirmacionReemplazaLaAnteriorEnLaMismaSesion() {
        String sesion = nuevaSesion();
        String tokenAnterior = confirmacionEliminacionService.generar(ID_CONTACTO, sesion);
        String tokenActual = confirmacionEliminacionService.generar(ID_CONTACTO, sesion);

        assertNotEquals(tokenAnterior, tokenActual);
        assertThrows(ConfirmacionInvalidaException.class,
                () -> confirmacionEliminacionService.consumir(ID_CONTACTO, sesion, tokenAnterior));

        confirmacionEliminacionService.consumir(ID_CONTACTO, sesion, tokenActual);

        assertEquals(1, contarFilas(sesion));
        assertEquals(0, contarConsumidas(sesion, hash(tokenAnterior)));
        assertEquals(1, contarConsumidas(sesion, hash(tokenActual)));
    }

    @Test
    void confirmacionesDeSesionesDiferentesSonIndependientes() {
        String sesionPrimera = nuevaSesion();
        String sesionSegunda = nuevaSesion();
        String tokenPrimero = confirmacionEliminacionService.generar(ID_CONTACTO, sesionPrimera);
        String tokenSegundo = confirmacionEliminacionService.generar(ID_CONTACTO, sesionSegunda);

        assertThrows(ConfirmacionInvalidaException.class,
                () -> confirmacionEliminacionService.consumir(ID_CONTACTO, sesionSegunda, tokenPrimero));
        assertThrows(ConfirmacionInvalidaException.class,
                () -> confirmacionEliminacionService.consumir(ID_CONTACTO, sesionPrimera, tokenSegundo));

        confirmacionEliminacionService.consumir(ID_CONTACTO, sesionPrimera, tokenPrimero);
        confirmacionEliminacionService.consumir(ID_CONTACTO, sesionSegunda, tokenSegundo);

        assertEquals(1, contarConsumidas(sesionPrimera, hash(tokenPrimero)));
        assertEquals(1, contarConsumidas(sesionSegunda, hash(tokenSegundo)));
    }

    private String nuevaSesion() {
        String sesion = UUID.randomUUID().toString();
        sesiones.add(sesion);
        return sesion;
    }

    private String tokenNuevo() {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]);
    }

    private String hash(String token) {
        try {
            byte[] resumen = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.US_ASCII));
            return HexFormat.of().formatHex(resumen);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 no está disponible", ex);
        }
    }

    private void insertarConfirmacionExpirada(String sesion, String tokenHash) {
        jdbcTemplate.update("""
                INSERT INTO confirmaciones_eliminacion
                    (session_id, token_hash, id_contacto, creado_en, expira_en, consumido_en)
                VALUES (?, ?, ?, DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 2 MINUTE),
                        DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL 1 MINUTE), NULL)
                """, sesion, tokenHash, ID_CONTACTO);
    }

    private int contarFilas(String sesion) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM confirmaciones_eliminacion WHERE session_id = ?",
                Integer.class, sesion);
    }

    private int contarConsumidas(String sesion, String tokenHash) {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM confirmaciones_eliminacion
                WHERE session_id = ? AND token_hash = ? AND consumido_en IS NOT NULL
                """, Integer.class, sesion, tokenHash);
    }
}
