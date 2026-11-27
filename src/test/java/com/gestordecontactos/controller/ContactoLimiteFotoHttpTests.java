package com.gestordecontactos.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=${SPRING_DATASOURCE_URL}",
        "spring.datasource.username=${SPRING_DATASOURCE_USERNAME}",
        "spring.datasource.password=${SPRING_DATASOURCE_PASSWORD}",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.session.store-type=jdbc",
        "spring.session.jdbc.table-name=SPRING_SESSION",
        "spring.session.jdbc.initialize-schema=never",
        "server.servlet.session.cookie.secure=false",
        "app.fotos.dir=${java.io.tmpdir}/gestor-contactos-test-fotos",
        "app.provisioning.email=",
        "app.provisioning.password=",
        "app.provisioning.nombre="
})
@ContextConfiguration(initializers = ContactoRegistroTests.VerificarBaseDeDatosDePrueba.class)
class ContactoLimiteFotoHttpTests {

    private static final int MAX_BYTES = 2_097_152;
    private static final String CLAVE_PRUEBA = "ClavePrueba123!";
    private static final String COOKIE_SESION = "GESTOR_SESSION";
    private static final Pattern CSRF_PATTERN =
            Pattern.compile("<input[^>]+name=\"_csrf\"[^>]+value=\"([^\"]+)\"");
    private static final AtomicInteger SECUENCIA = new AtomicInteger();

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String email;
    private String dni;

    @BeforeEach
    void crearUsuario() {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory() {
            @Override
            protected void prepareConnection(HttpURLConnection conexion, String metodo) throws IOException {
                super.prepareConnection(conexion, metodo);
                conexion.setFollowRedirects(false);
            }
        };
        restTemplate.getRestTemplate().setRequestFactory(fabrica);

        email = "limite-" + System.nanoTime() + "-" + SECUENCIA.incrementAndGet() + "@ejemplo.com";
        jdbcTemplate.update(
                "INSERT INTO usuarios (email, password_hash, nombre) VALUES (?, ?, ?)",
                email, new BCryptPasswordEncoder().encode(CLAVE_PRUEBA), "Usuario Limite");
    }

    @AfterEach
    void limpiar() {
        if (dni != null) {
            List<String> archivos = jdbcTemplate.query(
                    "SELECT foto_archivo FROM contactos WHERE dni = ?",
                    (resultado, fila) -> resultado.getString(1), dni);
            jdbcTemplate.update("DELETE FROM contactos WHERE dni = ?", dni);
            if (!archivos.isEmpty()) {
                try {
                    Files.deleteIfExists(directorioFotos().resolve(archivos.get(0)));
                } catch (IOException ex) {
                    throw new AssertionError(ex);
                }
            }
        }
        if (email != null) {
            jdbcTemplate.update("DELETE FROM usuarios WHERE email = ?", email);
        }
    }

    @Test
    void fotoValidaCercanaAlLimiteLlegaAlRegistro() throws IOException {
        dni = dniNuevo();
        Sesion sesion = autenticarse();

        ResponseEntity<String> respuesta = registrar(sesion, pngConTamano(MAX_BYTES - 1024), true);

        assertEquals(HttpStatus.FOUND, respuesta.getStatusCode());
        Long id = jdbcTemplate.queryForObject(
                "SELECT id_contacto FROM contactos WHERE dni = ?", Long.class, dni);
        assertNotNull(id);
        assertNotNull(respuesta.getHeaders().getLocation());
        assertTrue(respuesta.getHeaders().getLocation().toString().endsWith("/contactos/" + id));
    }

    @Test
    void fotoSuperiorAlLimiteVuelveAlFormularioConErrorControlado() throws IOException {
        dni = dniNuevo();
        Sesion sesion = autenticarse();

        ResponseEntity<String> respuesta = registrar(sesion, pngConTamano(MAX_BYTES + 1024), true);

        assertEquals(HttpStatus.OK, respuesta.getStatusCode());
        String cuerpo = respuesta.getBody();
        assertNotNull(cuerpo);
        assertTrue(cuerpo.contains("La fotografía no puede superar 2 MB"));
        assertTrue(cuerpo.contains("_csrf"));
        assertFalse(cuerpo.contains("MaxUploadSizeExceededException"));
        assertFalse(cuerpo.contains("java.lang"));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM contactos WHERE dni = ?", Integer.class, dni));
    }

    @Test
    void fotoSuperiorAlLimiteEnEdicionVuelveAlFormularioDeEdicion() throws IOException {
        dni = dniNuevo();
        String archivo = UUID.randomUUID() + ".png";
        Files.createDirectories(directorioFotos());
        Files.write(directorioFotos().resolve(archivo), pngBase());
        jdbcTemplate.update(
                "INSERT INTO contactos "
                        + "(dni, tipo_contacto, nombres, apellidos, foto_archivo, "
                        + "foto_content_type, foto_tamano_bytes) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                dni, "CLIENTE", "Ana", "Pérez", archivo, "image/png", 512);
        Long id = jdbcTemplate.queryForObject(
                "SELECT id_contacto FROM contactos WHERE dni = ?", Long.class, dni);
        assertNotNull(id);
        jdbcTemplate.update("INSERT INTO telefonos (id_contacto, numero) VALUES (?, ?)",
                id, "+51987654321");
        jdbcTemplate.update("INSERT INTO correos_electronicos (id_contacto, direccion) VALUES (?, ?)",
                id, "ana@ejemplo.com");
        Sesion sesion = autenticarse();

        ResponseEntity<String> respuesta = actualizar(sesion, id, pngConTamano(MAX_BYTES + 1024));

        assertEquals(HttpStatus.OK, respuesta.getStatusCode());
        String cuerpo = respuesta.getBody();
        assertNotNull(cuerpo);
        assertTrue(cuerpo.contains("Editar contacto"));
        assertTrue(cuerpo.contains("/contactos/" + id + "/editar"));
        assertTrue(cuerpo.contains("La fotografía no puede superar 2 MB"));
        assertTrue(cuerpo.contains("_csrf"));
        assertFalse(cuerpo.contains("Registrar contacto"));
        assertFalse(cuerpo.contains("MaxUploadSizeExceededException"));
        assertEquals(archivo, jdbcTemplate.queryForObject(
                "SELECT foto_archivo FROM contactos WHERE id_contacto = ?", String.class, id));
    }

    @Test
    void solicitudMultipartSinCsrfEsRechazada() throws IOException {
        dni = dniNuevo();
        Sesion sesion = autenticarse();

        ResponseEntity<String> respuesta = registrar(sesion, pngBase(), false);

        assertEquals(HttpStatus.FORBIDDEN, respuesta.getStatusCode());
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM contactos WHERE dni = ?", Integer.class, dni));
    }

    private Sesion autenticarse() {
        ResponseEntity<String> paginaLogin = restTemplate.getForEntity("/login", String.class);
        assertEquals(HttpStatus.OK, paginaLogin.getStatusCode());
        String tokenLogin = extraerCsrf(paginaLogin.getBody());
        String cookieInicial = valorCookie(
                paginaLogin.getHeaders().get(HttpHeaders.SET_COOKIE), COOKIE_SESION);
        assertNotNull(cookieInicial, "La página de login debe crear una sesión");

        HttpHeaders cabeceras = new HttpHeaders();
        cabeceras.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        cabeceras.add(HttpHeaders.COOKIE, cookie(COOKIE_SESION, cookieInicial));
        cabeceras.add("X-CSRF-TOKEN", tokenLogin);

        MultiValueMap<String, String> cuerpo = new LinkedMultiValueMap<>();
        cuerpo.add("username", email);
        cuerpo.add("password", CLAVE_PRUEBA);
        ResponseEntity<String> respuestaLogin = restTemplate.postForEntity(
                "/login", new HttpEntity<>(cuerpo, cabeceras), String.class);
        assertEquals(HttpStatus.FOUND, respuestaLogin.getStatusCode(),
                "El inicio de sesión HTTP no terminó en redirección.");

        String cookieAutenticada = valorCookie(
                respuestaLogin.getHeaders().get(HttpHeaders.SET_COOKIE), COOKIE_SESION);
        String cookie = cookieAutenticada != null ? cookieAutenticada : cookieInicial;

        HttpHeaders cabecerasFormulario = new HttpHeaders();
        cabecerasFormulario.add(HttpHeaders.COOKIE, cookie(COOKIE_SESION, cookie));
        ResponseEntity<String> formulario = restTemplate.exchange(
                "/contactos/nuevo", HttpMethod.GET, new HttpEntity<>(cabecerasFormulario), String.class);

        assertEquals(HttpStatus.OK, formulario.getStatusCode(), "La sesión no quedó autenticada.");
        assertNotNull(formulario.getBody());
        assertTrue(formulario.getBody().contains("Registrar contacto"),
                "La respuesta no es el formulario de contacto.");
        return new Sesion(cookie, extraerCsrf(formulario.getBody()));
    }

    private ResponseEntity<String> registrar(Sesion sesion, byte[] foto, boolean conCsrf) {
        ByteArrayResource archivo = new ByteArrayResource(foto) {
            @Override
            public String getFilename() {
                return "foto.png";
            }
        };

        MultiValueMap<String, Object> cuerpo = new LinkedMultiValueMap<>();
        cuerpo.add("nombres", "Ana");
        cuerpo.add("apellidos", "Pérez");
        cuerpo.add("dni", dni);
        cuerpo.add("tipoContacto", "CLIENTE");
        cuerpo.add("telefonos[0].numero", "987654321");
        cuerpo.add("correosElectronicos[0].direccion", "ana-" + dni + "@ejemplo.com");
        cuerpo.add("foto", archivo);

        HttpHeaders cabeceras = new HttpHeaders();
        cabeceras.add(HttpHeaders.COOKIE, cookie(COOKIE_SESION, sesion.cookie()));
        if (conCsrf) {
            cabeceras.add("X-CSRF-TOKEN", sesion.token());
        }
        return restTemplate.postForEntity(
                "/contactos", new HttpEntity<>(cuerpo, cabeceras), String.class);
    }

    private ResponseEntity<String> actualizar(Sesion sesion, Long id, byte[] foto) {
        ByteArrayResource archivo = new ByteArrayResource(foto) {
            @Override
            public String getFilename() {
                return "foto.png";
            }
        };

        MultiValueMap<String, Object> cuerpo = new LinkedMultiValueMap<>();
        cuerpo.add("nombres", "Ana Editada");
        cuerpo.add("apellidos", "Pérez");
        cuerpo.add("dni", dni);
        cuerpo.add("tipoContacto", "PROVEEDOR");
        cuerpo.add("telefonos[0].numero", "987654321");
        cuerpo.add("correosElectronicos[0].direccion", "ana-" + dni + "@ejemplo.com");
        cuerpo.add("foto", archivo);

        HttpHeaders cabeceras = new HttpHeaders();
        cabeceras.add(HttpHeaders.COOKIE, cookie(COOKIE_SESION, sesion.cookie()));
        cabeceras.add("X-CSRF-TOKEN", sesion.token());
        return restTemplate.postForEntity(
                "/contactos/" + id + "/editar", new HttpEntity<>(cuerpo, cabeceras), String.class);
    }

    private String extraerCsrf(String html) {
        assertNotNull(html, "La respuesta HTML es obligatoria");
        Matcher coincidencia = CSRF_PATTERN.matcher(html);
        assertTrue(coincidencia.find(), "No se encontró el token CSRF en el HTML");
        return coincidencia.group(1);
    }

    private String valorCookie(List<String> cabeceras, String nombre) {
        if (cabeceras == null) {
            return null;
        }
        for (String cabecera : cabeceras) {
            for (String parte : cabecera.split(";")) {
                String[] par = parte.trim().split("=", 2);
                if (par.length == 2 && par[0].equals(nombre)) {
                    return par[1];
                }
            }
        }
        return null;
    }

    private String cookie(String nombre, String valor) {
        return nombre + "=" + valor;
    }

    private Path directorioFotos() {
        return Path.of(System.getProperty("java.io.tmpdir"), "gestor-contactos-test-fotos");
    }

    private String dniNuevo() {
        long valor = Math.floorMod(System.nanoTime() + SECUENCIA.incrementAndGet(), 100_000_000L);
        return String.format(Locale.ROOT, "%08d", valor);
    }

    private byte[] pngConTamano(int tamanoObjetivo) throws IOException {
        byte[] base = pngBase();
        int finIhdr = 8 + 12 + 13;
        byte[] prefijo = Arrays.copyOf(base, finIhdr);
        byte[] sufijo = Arrays.copyOfRange(base, finIhdr, base.length);
        int longitudTexto = tamanoObjetivo - prefijo.length - sufijo.length - 12;
        assertTrue(longitudTexto > 0);

        byte[] datos = new byte[longitudTexto];
        byte[] clave = "Comentario\u0000".getBytes(StandardCharsets.ISO_8859_1);
        System.arraycopy(clave, 0, datos, 0, clave.length);
        Arrays.fill(datos, clave.length, datos.length, (byte) 'a');

        ByteArrayOutputStream salida = new ByteArrayOutputStream(tamanoObjetivo);
        salida.writeBytes(prefijo);
        salida.writeBytes(chunk("tEXt", datos));
        salida.writeBytes(sufijo);

        byte[] resultado = salida.toByteArray();
        assertEquals(tamanoObjetivo, resultado.length);
        return resultado;
    }

    private byte[] pngBase() throws IOException {
        BufferedImage imagen = new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(imagen, "png", salida));
        return salida.toByteArray();
    }

    private byte[] chunk(String tipo, byte[] datos) {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        byte[] tipoBytes = tipo.getBytes(StandardCharsets.US_ASCII);
        salida.writeBytes(ByteBuffer.allocate(4).putInt(datos.length).array());
        salida.writeBytes(tipoBytes);
        salida.writeBytes(datos);
        CRC32 crc = new CRC32();
        crc.update(tipoBytes);
        crc.update(datos);
        salida.writeBytes(ByteBuffer.allocate(4).putInt((int) crc.getValue()).array());
        return salida.toByteArray();
    }

    private record Sesion(String cookie, String token) {
    }
}
