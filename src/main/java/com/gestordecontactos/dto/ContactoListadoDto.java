package com.gestordecontactos.dto;

public record ContactoListadoDto(
        Long id,
        String nombres,
        String apellidos,
        String dni,
        String tipoContacto) {
}
