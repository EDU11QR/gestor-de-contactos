package com.gestordecontactos.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gestordecontactos.config.FotoProperties;
import com.gestordecontactos.dto.ContactoEdicionForm;
import com.gestordecontactos.dto.CorreoForm;
import com.gestordecontactos.dto.TelefonoForm;
import com.gestordecontactos.exception.ConflictoIntegridadException;
import com.gestordecontactos.service.ContactoService;
import com.gestordecontactos.service.FotoService;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
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
class ContactoEdicionIntegridadTests {

    private static final AtomicInteger SECUENCIA = new AtomicInteger();

    @Autowired
    private ContactoService contactoService;

    @Autowired
    private FotoService fotoService;

    @Autowired
    private FotoProperties fotoProperties;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final List<Long> identificadores = new ArrayList<>();
    private final List<String> archivos = new ArrayList<>();

    @AfterEach
    void limpiar() throws IOException {
        for (Long id : identificadores) {
            jdbcTemplate.update("DELETE FROM contactos WHERE id_contacto = ?", id);
        }
        for (String archivo : archivos) {
            Files.deleteIfExists(directorioFotos().resolve(archivo));
        }
    }

    @Test
    void noEliminaFotoAnteriorSiOtraReferenciaApareceAntesDelCallback() throws IOException {
        String archivoAnterior = registrarArchivo();
        String archivoNuevo = registrarArchivo();
        String archivoOtro = registrarArchivo();
        Long id = insertarContacto(dniNuevo(), archivoAnterior, List.of("+51987654321"),
                List.of("ana@ejemplo.com"));
        Long otroId = insertarContacto(dniNuevo(), archivoOtro, List.of("+51987654322"),
                List.of("otro@ejemplo.com"));
        TransactionTemplate transaccion = new TransactionTemplate(transactionManager);

        transaccion.executeWithoutResult(estado -> {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void beforeCommit(boolean soloLectura) {
                    jdbcTemplate.update(
                            "UPDATE contactos SET foto_archivo = ?, foto_content_type = ?, "
                                    + "foto_tamano_bytes = ? WHERE id_contacto = ?",
                            archivoNuevo, "image/png", 512, id);
                    jdbcTemplate.update(
                            "UPDATE contactos SET foto_archivo = ?, foto_content_type = ?, "
                                    + "foto_tamano_bytes = ? WHERE id_contacto = ?",
                            archivoAnterior, "image/png", 512, otroId);
                }
            });
            fotoService.eliminarTrasConfirmacion(archivoAnterior);
        });

        assertTrue(Files.exists(directorioFotos().resolve(archivoAnterior)));
        assertEquals(archivoNuevo, jdbcTemplate.queryForObject(
                "SELECT foto_archivo FROM contactos WHERE id_contacto = ?", String.class, id));
        assertEquals(archivoAnterior, jdbcTemplate.queryForObject(
                "SELECT foto_archivo FROM contactos WHERE id_contacto = ?", String.class, otroId));
    }

    @Test
    void rollbackTrasViolacionRealConservaTelefonosCorreosYFoto() throws IOException {
        String archivo = registrarArchivo();
        Long id = insertarContacto(dniNuevo(), archivo,
                List.of("+51987654321", "+51987654322"),
                List.of("ana@ejemplo.com", "segunda@ejemplo.com"));
        ContactoEdicionForm form = contactoService.obtenerFormularioEdicion(id);
        form.setNombres("Modificado");
        form.setApellidos("Modificado");
        form.getTelefonos().set(0, new TelefonoForm("987654321"));
        form.getTelefonos().set(1, new TelefonoForm("987654321"));
        form.getCorreosElectronicos().set(0, new CorreoForm("nuevo@ejemplo.com"));
        form.getCorreosElectronicos().set(1, new CorreoForm("otra-nueva@ejemplo.com"));

        ConflictoIntegridadException ex = assertThrows(ConflictoIntegridadException.class,
                () -> contactoService.actualizar(id, form));
        assertNotNull(ex.getCause());

        assertEquals("Existente", jdbcTemplate.queryForObject(
                "SELECT nombres FROM contactos WHERE id_contacto = ?", String.class, id));
        assertEquals("Contacto", jdbcTemplate.queryForObject(
                "SELECT apellidos FROM contactos WHERE id_contacto = ?", String.class, id));
        assertEquals(archivo, jdbcTemplate.queryForObject(
                "SELECT foto_archivo FROM contactos WHERE id_contacto = ?", String.class, id));
        assertEquals(List.of("+51987654321", "+51987654322"), telefonos(id));
        assertEquals(List.of("ana@ejemplo.com", "segunda@ejemplo.com"), correos(id));
        assertTrue(Files.exists(directorioFotos().resolve(archivo)));
    }

    private Long insertarContacto(String dni, String archivo, List<String> telefonos,
            List<String> correos) {
        jdbcTemplate.update(
                "INSERT INTO contactos "
                        + "(dni, tipo_contacto, nombres, apellidos, foto_archivo, "
                        + "foto_content_type, foto_tamano_bytes) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                dni, "CLIENTE", "Existente", "Contacto", archivo, "image/png", 512);
        Long id = jdbcTemplate.queryForObject(
                "SELECT id_contacto FROM contactos WHERE dni = ?", Long.class, dni);
        identificadores.add(id);
        for (String telefono : telefonos) {
            jdbcTemplate.update("INSERT INTO telefonos (id_contacto, numero) VALUES (?, ?)", id, telefono);
        }
        for (String correo : correos) {
            jdbcTemplate.update("INSERT INTO correos_electronicos (id_contacto, direccion) VALUES (?, ?)",
                    id, correo);
        }
        return id;
    }

    private String registrarArchivo() throws IOException {
        String archivo = UUID.randomUUID() + ".png";
        archivos.add(archivo);
        Files.createDirectories(directorioFotos());
        Files.write(directorioFotos().resolve(archivo), png());
        return archivo;
    }

    private Path directorioFotos() {
        return fotoProperties.getDir().toAbsolutePath().normalize();
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
}
