package com.gestordecontactos.service;

import com.gestordecontactos.config.FotoProperties;
import com.gestordecontactos.exception.FotoInvalidaException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Iterator;
import java.util.Locale;
import java.util.UUID;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FotoService {

    private static final int MAX_BYTES = 2_097_152;
    private static final int MAX_DIMENSION = 2000;
    private static final byte[] FIRMA_PNG = {
            (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A
    };
    private static final byte[] FIRMA_JPEG = {
            (byte) 0xFF, (byte) 0xD8, (byte) 0xFF
    };
    private static final byte[] CHUNK_APNG = {'a', 'c', 'T', 'L'};
    private static final Logger LOGGER = LoggerFactory.getLogger(FotoService.class);

    private final Path directorio;

    public FotoService(FotoProperties properties) {
        this.directorio = properties.getDir().toAbsolutePath().normalize();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public FotoGuardada guardar(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new FotoInvalidaException("La fotografía es obligatoria");
        }
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("FotoService requiere una transacción activa");
        }

        byte[] bytes;
        try {
            bytes = archivo.getBytes();
        } catch (IOException ex) {
            throw new FotoInvalidaException("No se pudo leer la fotografía", ex);
        }
        if (bytes.length == 0 || bytes.length > MAX_BYTES) {
            throw new FotoInvalidaException("La fotografía debe tener entre 1 byte y 2 MB");
        }

        String contentType = detectarContentType(bytes);
        if ("image/png".equals(contentType) && contieneChunkApng(bytes)) {
            throw new FotoInvalidaException("APNG no es un formato aceptado");
        }
        validarImagen(bytes);

        String extension = "image/png".equals(contentType) ? ".png" : ".jpg";
        String nombreArchivo = UUID.randomUUID() + extension;
        Path destino = directorio.resolve(nombreArchivo).normalize();
        if (!destino.startsWith(directorio)) {
            throw new FotoInvalidaException("La ruta de destino de la fotografía no es válida");
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    try {
                        Files.deleteIfExists(destino);
                    } catch (IOException ex) {
                        LOGGER.warn("No se pudo eliminar la fotografía no confirmada {}", nombreArchivo, ex);
                    }
                }
            }
        });

        Path temporal = null;
        try {
            Files.createDirectories(directorio);
            temporal = Files.createTempFile(directorio, "foto-", ".tmp");
            Files.write(temporal, bytes);
            try (FileChannel canal = FileChannel.open(temporal, StandardOpenOption.WRITE)) {
                canal.force(true);
            }
            Files.move(temporal, destino, StandardCopyOption.ATOMIC_MOVE);
            temporal = null;
        } catch (IOException ex) {
            throw new FotoInvalidaException("No se pudo almacenar la fotografía", ex);
        } finally {
            if (temporal != null) {
                try {
                    Files.deleteIfExists(temporal);
                } catch (IOException ex) {
                    LOGGER.warn("No se pudo eliminar el archivo temporal de fotografía", ex);
                }
            }
        }

        return new FotoGuardada(nombreArchivo, contentType, bytes.length);
    }

    private String detectarContentType(byte[] bytes) {
        if (empiezaCon(bytes, FIRMA_PNG)) {
            return "image/png";
        }
        if (empiezaCon(bytes, FIRMA_JPEG)) {
            return "image/jpeg";
        }
        throw new FotoInvalidaException("Solo se aceptan fotografías JPG, JPEG o PNG");
    }

    private void validarImagen(byte[] bytes) {
        try (ImageInputStream entrada = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> lectores = ImageIO.getImageReaders(entrada);
            if (!lectores.hasNext()) {
                throw new FotoInvalidaException("La fotografía no puede ser decodificada");
            }
            ImageReader lector = lectores.next();
            try {
                String formato = lector.getFormatName().toLowerCase(Locale.ROOT);
                if (!formato.equals("jpg") && !formato.equals("jpeg") && !formato.equals("png")) {
                    throw new FotoInvalidaException("El formato real de la fotografía no es aceptado");
                }
                lector.setInput(entrada);
                int ancho = lector.getWidth(0);
                int alto = lector.getHeight(0);
                if (ancho <= 0 || alto <= 0 || ancho > MAX_DIMENSION || alto > MAX_DIMENSION) {
                    throw new FotoInvalidaException("Las dimensiones máximas son 2000 × 2000 píxeles");
                }
                BufferedImage imagen = lector.read(0);
                if (imagen == null || imagen.getWidth() <= 0 || imagen.getHeight() <= 0
                        || imagen.getWidth() > MAX_DIMENSION || imagen.getHeight() > MAX_DIMENSION) {
                    throw new FotoInvalidaException("Las dimensiones máximas son 2000 × 2000 píxeles");
                }
            } finally {
                lector.dispose();
            }
        } catch (IOException ex) {
            throw new FotoInvalidaException("La fotografía no puede ser decodificada", ex);
        }
    }

    private boolean empiezaCon(byte[] bytes, byte[] prefijo) {
        if (bytes.length < prefijo.length) {
            return false;
        }
        for (int i = 0; i < prefijo.length; i++) {
            if (bytes[i] != prefijo[i]) {
                return false;
            }
        }
        return true;
    }

    private boolean contieneChunkApng(byte[] bytes) {
        int posicion = FIRMA_PNG.length;
        while (posicion + 12 <= bytes.length) {
            long longitud = Integer.toUnsignedLong(ByteBuffer.wrap(bytes, posicion, 4).getInt());
            if (longitud > (long) bytes.length - posicion - 12) {
                return false;
            }
            if (coincideTipoChunk(bytes, posicion + 4, CHUNK_APNG)) {
                return true;
            }
            posicion += 12 + (int) longitud;
        }
        return false;
    }

    private boolean coincideTipoChunk(byte[] bytes, int inicio, byte[] tipo) {
        for (int i = 0; i < tipo.length; i++) {
            if (bytes[inicio + i] != tipo[i]) {
                return false;
            }
        }
        return true;
    }

    public record FotoGuardada(String nombreArchivo, String contentType, int tamanoBytes) {
    }
}
