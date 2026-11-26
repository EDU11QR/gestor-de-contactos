package com.gestordecontactos.dto;

import java.util.List;

public record ContactoDetalleDto(
        Long id,
        String dni,
        String tipoContacto,
        String nombres,
        String apellidos,
        List<String> telefonos,
        List<String> correosElectronicos,
        String fotoContentType,
        int fotoTamanoBytes) {
}
