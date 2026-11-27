package com.gestordecontactos.service;

import com.gestordecontactos.exception.ConfirmacionInvalidaException;
import com.gestordecontactos.repository.ConfirmacionEliminacionRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConfirmacionEliminacionService {

    private static final int BYTES_TOKEN = 32;
    private static final int CARACTERES_TOKEN = 43;
    private static final Pattern TOKEN_PATTERN =
            Pattern.compile("[A-Za-z0-9_-]{" + CARACTERES_TOKEN + "}");

    private final ConfirmacionEliminacionRepository repositorio;
    private final SecureRandom aleatorio;
    private final long ttlMinutos;

    public ConfirmacionEliminacionService(ConfirmacionEliminacionRepository repositorio,
            @Value("${app.confirmaciones-eliminacion.ttl-minutos:10}") long ttlMinutos) {
        if (ttlMinutos <= 0) {
            throw new IllegalArgumentException("El TTL de la confirmación debe ser positivo");
        }
        this.repositorio = Objects.requireNonNull(repositorio);
        this.aleatorio = new SecureRandom();
        this.ttlMinutos = ttlMinutos;
    }

    public String generar(Long idContacto, String sessionId) {
        validarSesionYContacto(idContacto, sessionId);

        byte[] bytes = new byte[BYTES_TOKEN];
        aleatorio.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        repositorio.reemplazar(sessionId, hash(token), idContacto, ttlMinutos);
        return token;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void consumir(Long idContacto, String sessionId, String token) {
        validarSesionYContacto(idContacto, sessionId);
        String tokenValido = validarToken(token);
        int filasAfectadas = repositorio.consumir(sessionId, hash(tokenValido), idContacto);
        if (filasAfectadas != 1) {
            throw new ConfirmacionInvalidaException();
        }
    }

    private void validarSesionYContacto(Long idContacto, String sessionId) {
        if (idContacto == null || idContacto <= 0 || sessionId == null || sessionId.isBlank()) {
            throw new ConfirmacionInvalidaException();
        }
    }

    private String validarToken(String token) {
        if (token == null || !TOKEN_PATTERN.matcher(token).matches()) {
            throw new ConfirmacionInvalidaException();
        }
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(token);
            if (bytes.length != BYTES_TOKEN) {
                throw new ConfirmacionInvalidaException();
            }
        } catch (IllegalArgumentException ex) {
            throw new ConfirmacionInvalidaException();
        }
        return token;
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
}
