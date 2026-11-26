package com.gestordecontactos.controller;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

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
@AutoConfigureMockMvc
@Transactional
class ContactoRegistroTests {

    private static final AtomicInteger SECUENCIA = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void formularioRequiereAutenticacion() throws Exception {
        mockMvc.perform(get("/contactos/nuevo"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void formularioIncluyeTokenCsrf() throws Exception {
        mockMvc.perform(get("/contactos/nuevo").with(usuario()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("_csrf")))
                .andExpect(content().string(containsString("Registrar contacto")));
    }

    @Test
    void postSinCsrfEsRechazado() throws Exception {
        mockMvc.perform(solicitud(dniNuevo(), "ana@ejemplo.com", png(), "image/png").with(usuario()))
                .andExpect(status().isForbidden());
    }

    @Test
    void registroValidoPersisteDatosArchivoYMuestraDetalle() throws Exception {
        String dni = dniNuevo();
        String correo = "ANA-" + dni + "@Ejemplo.COM";

        MvcResult resultado = mockMvc.perform(solicitud(dni, correo, png(), "image/png")
                        .with(usuario()).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/contactos/*"))
                .andReturn();

        String ubicacion = resultado.getResponse().getRedirectedUrl();
        assertNotNull(ubicacion);
        Long id = jdbcTemplate.queryForObject(
                "SELECT id_contacto FROM contactos WHERE dni = ?", Long.class, dni);
        assertNotNull(id);
        assertEquals("/contactos/" + id, ubicacion);
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM telefonos WHERE id_contacto = ?", Integer.class, id));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM correos_electronicos WHERE id_contacto = ?", Integer.class, id));
        assertEquals("+51987654321", jdbcTemplate.queryForObject(
                "SELECT numero FROM telefonos WHERE id_contacto = ?", String.class, id));
        assertEquals(correo.toLowerCase(Locale.ROOT), jdbcTemplate.queryForObject(
                "SELECT direccion FROM correos_electronicos WHERE id_contacto = ?", String.class, id));

        String nombreArchivo = jdbcTemplate.queryForObject(
                "SELECT foto_archivo FROM contactos WHERE id_contacto = ?", String.class, id);
        assertNotNull(nombreArchivo);
        assertTrue(Files.exists(directorioFotos().resolve(nombreArchivo)));

        mockMvc.perform(get(ubicacion).with(usuario()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ana")))
                .andExpect(content().string(containsString(dni)))
                .andExpect(content().string(containsString("+51987654321")))
                .andExpect(content().string(containsString(correo.toLowerCase(Locale.ROOT))));
    }

    @Test
    void dniDuplicadoMuestraErrorYNoCreaContacto() throws Exception {
        String dni = dniNuevo();
        insertarContactoBase(dni);

        mockMvc.perform(solicitud(dni, "ana@ejemplo.com", png(), "image/png")
                        .with(usuario()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ya existe un contacto con ese DNI")));

        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM contactos WHERE dni = ?", Integer.class, dni));
    }

    @Test
    void fotoInvalidaMuestraErrorYNoCreaContacto() throws Exception {
        String dni = dniNuevo();

        mockMvc.perform(solicitud(dni, "ana@ejemplo.com",
                        "no-imagen".getBytes(StandardCharsets.UTF_8), "text/plain")
                        .with(usuario()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Solo se aceptan fotografías")));

        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM contactos WHERE dni = ?", Integer.class, dni));
    }

    @Test
    void baseRechazaDniDuplicado() {
        String dni = dniNuevo();
        insertarContactoBase(dni);

        assertThrows(DataIntegrityViolationException.class,
                () -> jdbcTemplate.update(
                        "INSERT INTO contactos "
                                + "(dni, tipo_contacto, nombres, apellidos, foto_archivo, "
                                + "foto_content_type, foto_tamano_bytes) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                        dni, "PROVEEDOR", "Otro", "Contacto", UUID.randomUUID() + ".png", "image/png", 1024));
    }

    private void insertarContactoBase(String dni) {
        jdbcTemplate.update(
                "INSERT INTO contactos "
                        + "(dni, tipo_contacto, nombres, apellidos, foto_archivo, "
                        + "foto_content_type, foto_tamano_bytes) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                dni, "CLIENTE", "Existente", "Contacto", UUID.randomUUID() + ".png", "image/png", 1024);
    }

    private MockHttpServletRequestBuilder solicitud(String dni, String correo, byte[] foto,
            String contentType) {
        return multipart("/contactos")
                .file(new MockMultipartFile("foto", "foto.png", contentType, foto))
                .param("nombres", " Ana ")
                .param("apellidos", " Pérez ")
                .param("dni", dni)
                .param("tipoContacto", "CLIENTE")
                .param("telefonos[0].numero", "987654321")
                .param("correosElectronicos[0].direccion", correo);
    }

    private static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
            .UserRequestPostProcessor usuario() {
        return user("prueba").roles("USER");
    }

    private Path directorioFotos() {
        return Path.of(System.getProperty("java.io.tmpdir"), "gestor-contactos-test-fotos");
    }

    private String dniNuevo() {
        long valor = Math.floorMod(System.nanoTime() + SECUENCIA.incrementAndGet(), 100_000_000L);
        return String.format(Locale.ROOT, "%08d", valor);
    }

    private byte[] png() throws Exception {
        BufferedImage imagen = new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(imagen, "png", salida));
        return salida.toByteArray();
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
