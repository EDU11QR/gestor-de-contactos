package com.gestordecontactos.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.gestordecontactos.config.FotoProperties;
import com.gestordecontactos.exception.FotoInvalidaException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class FotoServiceTests {

    private static final byte[] FIRMA_PNG = {
            (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A
    };

    @TempDir
    Path directorio;

    private FotoService service;
    private FotoReferenciaService referenciaService;

    @BeforeEach
    void inicializar() {
        FotoProperties properties = new FotoProperties();
        properties.setDir(directorio);
        referenciaService = mock(FotoReferenciaService.class);
        service = new FotoService(properties, referenciaService);
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
    }

    @AfterEach
    void limpiar() {
        TransactionSynchronizationManager.clear();
    }

    @Test
    void guardaPngValidoEnDirectorioPrivado() throws IOException {
        byte[] png = png(20, 20);

        FotoService.FotoGuardada guardado = service.guardar(multipart(png, "image/png"));

        assertTrue(guardado.nombreArchivo().endsWith(".png"));
        assertEquals("image/png", guardado.contentType());
        assertEquals(png.length, guardado.tamanoBytes());
        assertTrue(Files.exists(directorio.resolve(guardado.nombreArchivo())));
        try (Stream<Path> archivos = Files.list(directorio)) {
            assertEquals(1, archivos.count());
        }
    }

    @Test
    void guardaJpegValido() throws IOException {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        BufferedImage imagen = new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB);
        assertTrue(ImageIO.write(imagen, "jpg", salida));

        FotoService.FotoGuardada guardado =
                service.guardar(multipart(salida.toByteArray(), "image/jpeg"));

        assertTrue(guardado.nombreArchivo().endsWith(".jpg"));
        assertEquals("image/jpeg", guardado.contentType());
        assertTrue(Files.exists(directorio.resolve(guardado.nombreArchivo())));
    }

    @Test
    void rechazaContenidoQueNoEsImagen() {
        FotoInvalidaException ex = assertThrows(FotoInvalidaException.class,
                () -> service.guardar(multipart("texto".getBytes(StandardCharsets.UTF_8), "text/plain")));
        assertTrue(ex.getMessage().contains("Solo se aceptan"));
    }

    @Test
    void rechazaApng() {
        FotoInvalidaException ex = assertThrows(FotoInvalidaException.class,
                () -> service.guardar(multipart(pngConChunkApng(), "image/png")));

        assertTrue(ex.getMessage().contains("APNG"));
        assertDirectorioVacio();
    }

    @Test
    void aceptaPngConSecuenciaAcTLEnDatosDeTexto() throws IOException {
        FotoService.FotoGuardada guardado = service.guardar(multipart(pngConTextoAcTL(), "image/png"));

        assertTrue(guardado.nombreArchivo().endsWith(".png"));
        assertTrue(Files.exists(directorio.resolve(guardado.nombreArchivo())));
    }

    @Test
    void rechazaDimensionesExcesivasAntesDeDecodificar() {
        FotoInvalidaException ex = assertThrows(FotoInvalidaException.class,
                () -> service.guardar(multipart(pngConCabecera(2001, 1), "image/png")));

        assertTrue(ex.getMessage().contains("dimensiones máximas"));
        assertDirectorioVacio();
    }

    @Test
    void rechazaArchivoMayorADosMegabytes() {
        byte[] grande = new byte[2_097_153];
        assertThrows(FotoInvalidaException.class,
                () -> service.guardar(multipart(grande, "image/png")));
        assertDirectorioVacio();
    }

    @Test
    void rechazaDimensionesSuperioresA2000Pixeles() throws IOException {
        assertThrows(FotoInvalidaException.class,
                () -> service.guardar(multipart(png(2001, 1), "image/png")));
        assertDirectorioVacio();
    }

    @Test
    void eliminaArchivoSiLaTransaccionSeRevierte() throws IOException {
        FotoService.FotoGuardada guardado = service.guardar(multipart(png(10, 10), "image/png"));
        Path archivo = directorio.resolve(guardado.nombreArchivo());
        assertTrue(Files.exists(archivo));

        new ArrayList<>(TransactionSynchronizationManager.getSynchronizations())
                .forEach(sincronizacion -> sincronizacion.afterCompletion(
                        TransactionSynchronization.STATUS_ROLLED_BACK));

        assertFalse(Files.exists(archivo));
    }

    @Test
    void conservaArchivoSiLaTransaccionSeConfirma() throws IOException {
        FotoService.FotoGuardada guardado = service.guardar(multipart(png(10, 10), "image/png"));
        Path archivo = directorio.resolve(guardado.nombreArchivo());

        new ArrayList<>(TransactionSynchronizationManager.getSynchronizations())
                .forEach(sincronizacion -> sincronizacion.afterCompletion(
                        TransactionSynchronization.STATUS_COMMITTED));

        assertTrue(Files.exists(archivo));
    }

    @Test
    void eliminaTemporalesSiLaEscrituraFallaDespuesDeRegistrarSincronizacion() throws IOException {
        Path directorioSoloLectura = directorio.resolve("solo-lectura");
        Files.createDirectory(directorioSoloLectura);
        Set<PosixFilePermission> permisosOriginales = Files.getPosixFilePermissions(directorioSoloLectura);
        Set<PosixFilePermission> permisosSinEscritura = EnumSet.copyOf(permisosOriginales);
        permisosSinEscritura.remove(PosixFilePermission.OWNER_WRITE);
        permisosSinEscritura.remove(PosixFilePermission.GROUP_WRITE);
        permisosSinEscritura.remove(PosixFilePermission.OTHERS_WRITE);
        Files.setPosixFilePermissions(directorioSoloLectura, permisosSinEscritura);

        FotoProperties properties = new FotoProperties();
        properties.setDir(directorioSoloLectura);
        FotoService servicioConFallo = new FotoService(properties, referenciaService);
        try {
            FotoInvalidaException ex = assertThrows(FotoInvalidaException.class,
                    () -> servicioConFallo.guardar(multipart(png(10, 10), "image/png")));
            assertTrue(ex.getMessage().contains("almacenar"));

            new ArrayList<>(TransactionSynchronizationManager.getSynchronizations())
                    .forEach(sincronizacion -> sincronizacion.afterCompletion(
                            TransactionSynchronization.STATUS_ROLLED_BACK));

            try (Stream<Path> archivos = Files.list(directorioSoloLectura)) {
                assertEquals(0, archivos.count());
            }
        } finally {
            Files.setPosixFilePermissions(directorioSoloLectura, permisosOriginales);
        }
    }

    @Test
    void eliminaFotoAnteriorSoloSiLaTransaccionSeConfirma() throws IOException {
        Path anterior = directorio.resolve("anterior.png");
        Files.write(anterior, png(10, 10));
        when(referenciaService.estaReferenciada("anterior.png")).thenReturn(false);

        service.eliminarTrasConfirmacion("anterior.png");

        new ArrayList<>(TransactionSynchronizationManager.getSynchronizations())
                .forEach(sincronizacion -> sincronizacion.afterCompletion(
                        TransactionSynchronization.STATUS_ROLLED_BACK));
        assertTrue(Files.exists(anterior));

        new ArrayList<>(TransactionSynchronizationManager.getSynchronizations())
                .forEach(TransactionSynchronization::afterCommit);
        assertFalse(Files.exists(anterior));
    }

    @Test
    void noEliminaFotoAnteriorSiSigueReferenciada() throws IOException {
        Path anterior = directorio.resolve("anterior.png");
        Files.write(anterior, png(10, 10));
        when(referenciaService.estaReferenciada("anterior.png")).thenReturn(true);

        service.eliminarTrasConfirmacion("anterior.png");

        new ArrayList<>(TransactionSynchronizationManager.getSynchronizations())
                .forEach(TransactionSynchronization::afterCommit);
        assertTrue(Files.exists(anterior));
    }

    @Test
    void requiereTransaccionActiva() throws IOException {
        TransactionSynchronizationManager.clear();
        TransactionSynchronizationManager.setActualTransactionActive(false);

        assertThrows(IllegalStateException.class,
                () -> service.guardar(multipart(png(10, 10), "image/png")));
        assertDirectorioVacio();
    }

    private void assertDirectorioVacio() {
        try (Stream<Path> archivos = Files.list(directorio)) {
            assertEquals(0, archivos.count());
        } catch (IOException ex) {
            throw new AssertionError(ex);
        }
    }

    private MockMultipartFile multipart(byte[] bytes, String contentType) {
        return new MockMultipartFile("foto", "foto.img", contentType, bytes);
    }

    private byte[] png(int ancho, int alto) throws IOException {
        BufferedImage imagen = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(imagen, "png", salida));
        return salida.toByteArray();
    }

    private byte[] pngConChunkApng() {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        salida.writeBytes(FIRMA_PNG);
        salida.writeBytes(chunk("IHDR", cabecera(10, 10)));
        salida.writeBytes(chunk("acTL", new byte[8]));
        return salida.toByteArray();
    }

    private byte[] pngConCabecera(int ancho, int alto) {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        salida.writeBytes(FIRMA_PNG);
        salida.writeBytes(chunk("IHDR", cabecera(ancho, alto)));
        return salida.toByteArray();
    }

    private byte[] pngConTextoAcTL() throws IOException {
        byte[] png = png(20, 20);
        int finIhdr = FIRMA_PNG.length + 12 + 13;
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        salida.write(png, 0, finIhdr);
        salida.writeBytes(chunk("tEXt", "Comentario\0acTL".getBytes(StandardCharsets.US_ASCII)));
        salida.write(png, finIhdr, png.length - finIhdr);
        return salida.toByteArray();
    }

    private byte[] cabecera(int ancho, int alto) {
        ByteBuffer datos = ByteBuffer.allocate(13);
        datos.putInt(ancho).putInt(alto).put((byte) 8).put((byte) 2)
                .put((byte) 0).put((byte) 0).put((byte) 0);
        return datos.array();
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
}
