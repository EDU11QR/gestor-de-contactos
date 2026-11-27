package com.gestordecontactos.controller;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gestordecontactos.dto.TipoContacto;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
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
@ContextConfiguration(initializers = ContactoEdicionTests.VerificarBaseDeDatosDePrueba.class)
@AutoConfigureMockMvc
@Transactional
class ContactoEdicionTests {

    private static final AtomicInteger SECUENCIA = new AtomicInteger();

    private final List<String> archivos = new ArrayList<>();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void limpiarArchivos() throws IOException {
        for (String nombre : archivos) {
            Files.deleteIfExists(directorioFotos().resolve(nombre));
        }
        archivos.clear();
    }

    @Test
    void formularioEdicionRequiereAutenticacion() throws Exception {
        Long id = insertarContacto(dniNuevo(), "ana@ejemplo.com", "CLIENTE");

        mockMvc.perform(get("/contactos/{id}/editar", id))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void formularioMuestraDatosActualesYTokenCsrf() throws Exception {
        String dni = dniNuevo();
        Long id = insertarContacto(dni, "ana@ejemplo.com", "AMBOS");

        mockMvc.perform(get("/contactos/{id}/editar", id).with(usuario()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("_csrf")))
                .andExpect(content().string(containsString("Editar contacto")))
                .andExpect(content().string(containsString("/contactos/" + id + "/editar")))
                .andExpect(content().string(containsString("Existente")))
                .andExpect(content().string(containsString("Contacto")))
                .andExpect(content().string(containsString(dni)))
                .andExpect(content().string(containsString("+51987654321")))
                .andExpect(content().string(containsString("ana@ejemplo.com")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"CLIENTE", "PROVEEDOR", "AMBOS"})
    void formularioMuestraClasificacionGuardadaSeleccionada(String tipo) throws Exception {
        String dni = dniNuevo();
        Long id = insertarContacto(dni, "ana@ejemplo.com", tipo);

        String cuerpo = mockMvc.perform(get("/contactos/{id}/editar", id).with(usuario()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        for (TipoContacto opcion : TipoContacto.values()) {
            assertTrue(cuerpo.contains("value=\"" + opcion.name() + "\""),
                    "La opción " + opcion + " no está disponible");
        }
        assertOpcionSeleccionada(cuerpo, tipo);
    }

    @Test
    void formularioMuestraTodosLosTelefonosYCorreosYControlesDeFilas() throws Exception {
        String dni = dniNuevo();
        Long id = insertarContacto(dni, "AMBOS",
                List.of("+51987654321", "+51987654322"),
                List.of("ana@ejemplo.com", "segunda@ejemplo.com"));

        mockMvc.perform(get("/contactos/{id}/editar", id).with(usuario()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("+51987654321")))
                .andExpect(content().string(containsString("+51987654322")))
                .andExpect(content().string(containsString("ana@ejemplo.com")))
                .andExpect(content().string(containsString("segunda@ejemplo.com")))
                .andExpect(content().string(containsString("agregar-telefono")))
                .andExpect(content().string(containsString("quitar-telefono")))
                .andExpect(content().string(containsString("agregar-correo")))
                .andExpect(content().string(containsString("quitar-correo")));
    }

    @Test
    void actualizacionEditaMultiplesFilasYConservaLasDemas() throws Exception {
        String dni = dniNuevo();
        Long id = insertarContacto(dni, "CLIENTE",
                List.of("+51987654321", "+51987654322"),
                List.of("ana@ejemplo.com", "segunda@ejemplo.com"));

        mockMvc.perform(multipart("/contactos/{id}/editar", id)
                        .param("nombres", "Ana Editada")
                        .param("apellidos", "López")
                        .param("dni", dni)
                        .param("tipoContacto", "PROVEEDOR")
                        .param("telefonos[0].numero", "987654323")
                        .param("telefonos[1].numero", "987654322")
                        .param("correosElectronicos[0].direccion", "NUEVO@Ejemplo.COM")
                        .param("correosElectronicos[1].direccion", "segunda@ejemplo.com")
                        .with(usuario()).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/contactos/*"));

        List<String> telefonos = telefonos(id);
        List<String> correos = correos(id);
        assertEquals(2, telefonos.size());
        assertTrue(telefonos.contains("+51987654323"));
        assertTrue(telefonos.contains("+51987654322"));
        assertEquals(2, correos.size());
        assertTrue(correos.contains("nuevo@ejemplo.com"));
        assertTrue(correos.contains("segunda@ejemplo.com"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"CLIENTE", "PROVEEDOR", "AMBOS"})
    void actualizacionModificaClasificacionYPersisteEnMysql(String tipoDestino) throws Exception {
        String dni = dniNuevo();
        String tipoInicial = "CLIENTE".equals(tipoDestino) ? "PROVEEDOR" : "CLIENTE";
        Long id = insertarContacto(dni, "ana@ejemplo.com", tipoInicial);

        mockMvc.perform(solicitudConClasificacion(id, dni, "987654329", "ana@ejemplo.com", tipoDestino)
                        .with(usuario()).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/contactos/*"));

        assertEquals(tipoDestino, jdbcTemplate.queryForObject(
                "SELECT tipo_contacto FROM contactos WHERE id_contacto = ?", String.class, id));
    }

    @Test
    void actualizacionRechazaClasificacionVacia() throws Exception {
        String dni = dniNuevo();
        Long id = insertarContacto(dni, "ana@ejemplo.com", "CLIENTE");

        mockMvc.perform(solicitudConClasificacion(id, dni, "987654321", "ana@ejemplo.com", "")
                        .with(usuario()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("El tipo de contacto es obligatorio")))
                .andExpect(content().string(containsString("Seleccione...")));

        assertEquals("CLIENTE", jdbcTemplate.queryForObject(
                "SELECT tipo_contacto FROM contactos WHERE id_contacto = ?", String.class, id));
    }

    @Test
    void actualizacionPermiteQuitarFilasManteniendoAlMenosUna() throws Exception {
        String dni = dniNuevo();
        Long id = insertarContacto(dni, "CLIENTE",
                List.of("+51987654321", "+51987654322"),
                List.of("ana@ejemplo.com", "segunda@ejemplo.com"));

        mockMvc.perform(solicitudSinFoto(id, dni, "987654323", "nuevo@ejemplo.com")
                        .with(usuario()).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/contactos/*"));

        assertEquals(List.of("+51987654323"), telefonos(id));
        assertEquals(List.of("nuevo@ejemplo.com"), correos(id));
    }

    @Test
    void actualizacionRechazaFormularioSinTelefonoNiCorreo() throws Exception {
        String dni = dniNuevo();
        Long id = insertarContacto(dni, "ana@ejemplo.com", "CLIENTE");

        mockMvc.perform(multipart("/contactos/{id}/editar", id)
                        .param("nombres", "Ana Editada")
                        .param("apellidos", "López")
                        .param("dni", dni)
                        .param("tipoContacto", "PROVEEDOR")
                        .with(usuario()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Debe registrar al menos un teléfono")))
                .andExpect(content().string(containsString("Debe registrar al menos un correo electrónico")))
                .andExpect(content().string(containsString("telefonos[0].numero")))
                .andExpect(content().string(containsString("correosElectronicos[0].direccion")));

        assertEquals(List.of("+51987654321"), telefonos(id));
        assertEquals(List.of("ana@ejemplo.com"), correos(id));
    }

    @Test
    void actualizacionValidaPersisteCambiosConservaDniYFoto() throws Exception {
        String dni = dniNuevo();
        Long id = insertarContacto(dni, "ana@ejemplo.com", "CLIENTE");
        String archivoAnterior = archivoFoto(id);

        MvcResult resultado = mockMvc.perform(solicitudSinFoto(id, dni, "987654322", "NUEVO@Ejemplo.COM")
                        .with(usuario()).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/contactos/*"))
                .andExpect(flash().attribute("actualizado", true))
                .andReturn();

        assertEquals("/contactos/" + id, resultado.getResponse().getRedirectedUrl());
        assertEquals("Ana Editada", jdbcTemplate.queryForObject(
                "SELECT nombres FROM contactos WHERE id_contacto = ?", String.class, id));
        assertEquals("López", jdbcTemplate.queryForObject(
                "SELECT apellidos FROM contactos WHERE id_contacto = ?", String.class, id));
        assertEquals("PROVEEDOR", jdbcTemplate.queryForObject(
                "SELECT tipo_contacto FROM contactos WHERE id_contacto = ?", String.class, id));
        assertEquals("+51987654322", jdbcTemplate.queryForObject(
                "SELECT numero FROM telefonos WHERE id_contacto = ?", String.class, id));
        assertEquals("nuevo@ejemplo.com", jdbcTemplate.queryForObject(
                "SELECT direccion FROM correos_electronicos WHERE id_contacto = ?", String.class, id));
        assertEquals(archivoAnterior, archivoFoto(id));
        assertTrue(Files.exists(directorioFotos().resolve(archivoAnterior)));

        mockMvc.perform(get("/contactos/{id}", id).with(usuario()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ana Editada")))
                .andExpect(content().string(containsString(dni)));
    }

    @Test
    void reemplazaFotoYActualizaMetadatos() throws Exception {
        Long id = insertarContacto(dniNuevo(), "ana@ejemplo.com", "CLIENTE");
        String archivoAnterior = archivoFoto(id);
        byte[] nuevaFoto = png();

        mockMvc.perform(solicitudConFoto(id, dniNuevo(), "987654321", "ana@ejemplo.com",
                        nuevaFoto, "image/png").with(usuario()).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/contactos/*"));

        String archivoNuevo = archivoFoto(id);
        assertNotEquals(archivoAnterior, archivoNuevo);
        archivos.add(archivoNuevo);
        assertEquals("image/png", jdbcTemplate.queryForObject(
                "SELECT foto_content_type FROM contactos WHERE id_contacto = ?", String.class, id));
        assertEquals(nuevaFoto.length, (int) jdbcTemplate.queryForObject(
                "SELECT foto_tamano_bytes FROM contactos WHERE id_contacto = ?", Integer.class, id));
        assertTrue(Files.exists(directorioFotos().resolve(archivoNuevo)));
    }

    @Test
    void dniDuplicadoDeOtroContactoMuestraErrorYNoModificaDatosNiArchivos() throws Exception {
        String dniDestino = dniNuevo();
        String dniOtro = dniNuevo();
        Long id = insertarContacto(dniDestino, "ana@ejemplo.com", "CLIENTE");
        insertarContacto(dniOtro, "otro@ejemplo.com", "PROVEEDOR");
        String archivoAnterior = archivoFoto(id);
        long archivosAntes = contarArchivos();

        mockMvc.perform(solicitudConFoto(id, dniOtro, "987654321", "ana@ejemplo.com",
                        png(), "image/png").with(usuario()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ya existe un contacto con ese DNI")));

        assertEquals("Existente", jdbcTemplate.queryForObject(
                "SELECT nombres FROM contactos WHERE id_contacto = ?", String.class, id));
        assertEquals(dniDestino, jdbcTemplate.queryForObject(
                "SELECT dni FROM contactos WHERE id_contacto = ?", String.class, id));
        assertEquals(archivoAnterior, archivoFoto(id));
        assertEquals(archivosAntes, contarArchivos());
    }

    @Test
    void telefonoYCorreoInvalidosMuestranErroresYNoModificanContacto() throws Exception {
        String dni = dniNuevo();
        Long id = insertarContacto(dni, "ana@ejemplo.com", "CLIENTE");
        String archivoAnterior = archivoFoto(id);

        mockMvc.perform(solicitudSinFoto(id, dni, "+59998765432", "correo-invalido")
                        .with(usuario()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("El teléfono debe ser peruano")))
                .andExpect(content().string(containsString("El correo electrónico no tiene un formato válido")));

        assertEquals("+51987654321", jdbcTemplate.queryForObject(
                "SELECT numero FROM telefonos WHERE id_contacto = ?", String.class, id));
        assertEquals("ana@ejemplo.com", jdbcTemplate.queryForObject(
                "SELECT direccion FROM correos_electronicos WHERE id_contacto = ?", String.class, id));
        assertEquals(archivoAnterior, archivoFoto(id));
    }

    @Test
    void contactoInexistenteDevuelve404EnGetYPost() throws Exception {
        Long idInexistente = Long.MAX_VALUE;

        mockMvc.perform(get("/contactos/{id}/editar", idInexistente).with(usuario()))
                .andExpect(status().isNotFound());

        mockMvc.perform(solicitudSinFoto(idInexistente, dniNuevo(), "987654321", "ana@ejemplo.com")
                        .with(usuario()).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void postSinCsrfEsRechazado() throws Exception {
        Long id = insertarContacto(dniNuevo(), "ana@ejemplo.com", "CLIENTE");

        mockMvc.perform(solicitudSinFoto(id, dniNuevo(), "987654321", "ana@ejemplo.com")
                        .with(usuario()))
                .andExpect(status().isForbidden());
    }

    @Test
    void fotoInvalidaNoCambiaNiEliminaLaFotoAnterior() throws Exception {
        Long id = insertarContacto(dniNuevo(), "ana@ejemplo.com", "CLIENTE");
        String archivoAnterior = archivoFoto(id);

        mockMvc.perform(solicitudConFoto(id, dniNuevo(), "987654321", "ana@ejemplo.com",
                        "no-imagen".getBytes(StandardCharsets.UTF_8), "text/plain")
                        .with(usuario()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Solo se aceptan fotografías")));

        assertEquals(archivoAnterior, archivoFoto(id));
        assertTrue(Files.exists(directorioFotos().resolve(archivoAnterior)));
    }

    private Long insertarContacto(String dni, String correo, String tipo) throws IOException {
        return insertarContacto(dni, tipo, List.of("+51987654321"), List.of(correo));
    }

    private Long insertarContacto(String dni, String tipo, List<String> telefonos,
            List<String> correos) throws IOException {
        String archivo = UUID.randomUUID() + ".png";
        archivos.add(archivo);
        Files.createDirectories(directorioFotos());
        Files.write(directorioFotos().resolve(archivo), png());

        jdbcTemplate.update(
                "INSERT INTO contactos "
                        + "(dni, tipo_contacto, nombres, apellidos, foto_archivo, "
                        + "foto_content_type, foto_tamano_bytes) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                dni, tipo, "Existente", "Contacto", archivo, "image/png", 512);
        Long id = jdbcTemplate.queryForObject(
                "SELECT id_contacto FROM contactos WHERE dni = ?", Long.class, dni);
        assertNotNull(id);
        for (String telefono : telefonos) {
            jdbcTemplate.update("INSERT INTO telefonos (id_contacto, numero) VALUES (?, ?)", id, telefono);
        }
        for (String correo : correos) {
            jdbcTemplate.update("INSERT INTO correos_electronicos (id_contacto, direccion) VALUES (?, ?)",
                    id, correo);
        }
        return id;
    }

    private String archivoFoto(Long id) {
        return jdbcTemplate.queryForObject(
                "SELECT foto_archivo FROM contactos WHERE id_contacto = ?", String.class, id);
    }

    private List<String> telefonos(Long id) {
        return jdbcTemplate.queryForList(
                "SELECT numero FROM telefonos WHERE id_contacto = ? ORDER BY id_telefono", String.class, id);
    }

    private List<String> correos(Long id) {
        return jdbcTemplate.queryForList(
                "SELECT direccion FROM correos_electronicos WHERE id_contacto = ? ORDER BY id_correo",
                String.class, id);
    }

    private void assertOpcionSeleccionada(String cuerpo, String tipo) {
        Pattern patron = Pattern.compile(
                "<option\\b(?=[^>]*\\bvalue=\"" + tipo + "\")(?=[^>]*\\bselected(?:=\"[^\"]*\")?)[^>]*>",
                Pattern.CASE_INSENSITIVE);
        assertTrue(patron.matcher(cuerpo).find(),
                "La opción " + tipo + " no está seleccionada");
    }

    private long contarArchivos() throws IOException {
        if (!Files.exists(directorioFotos())) {
            return 0;
        }
        try (Stream<Path> archivosEnDirectorio = Files.list(directorioFotos())) {
            return archivosEnDirectorio.count();
        }
    }

    private MockHttpServletRequestBuilder solicitudSinFoto(Long id, String dni, String telefono,
            String correo) {
        return solicitudConClasificacion(id, dni, telefono, correo, "PROVEEDOR");
    }

    private MockHttpServletRequestBuilder solicitudConClasificacion(Long id, String dni,
            String telefono, String correo, String tipoContacto) {
        return multipart("/contactos/{id}/editar", id)
                .param("nombres", " Ana Editada ")
                .param("apellidos", " López ")
                .param("dni", dni)
                .param("tipoContacto", tipoContacto)
                .param("telefonos[0].numero", telefono)
                .param("correosElectronicos[0].direccion", correo);
    }

    private MockHttpServletRequestBuilder solicitudConFoto(Long id, String dni, String telefono,
            String correo, byte[] foto, String contentType) {
        return multipart("/contactos/{id}/editar", id)
                .file(new MockMultipartFile("foto", "foto.png", contentType, foto))
                .param("nombres", " Ana Editada ")
                .param("apellidos", " López ")
                .param("dni", dni)
                .param("tipoContacto", "PROVEEDOR")
                .param("telefonos[0].numero", telefono)
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

    private byte[] png() throws IOException {
        BufferedImage imagen = new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(imagen, "png", salida));
        return salida.toByteArray();
    }

    public static class VerificarBaseDeDatosDePrueba
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
