package com.gestordecontactos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class TelefonoForm {

    @NotBlank(message = "El teléfono es obligatorio")
    @Pattern(regexp = "^(?:\\+51|51)?9\\d{8}$",
            message = "El teléfono debe ser peruano: 9 dígitos, opcionalmente precedido de +51 o 51")
    private String numero;

    public TelefonoForm() {
    }

    public TelefonoForm(String numero) {
        this.numero = numero;
    }

    public String getNumero() {
        return numero;
    }

    public void setNumero(String numero) {
        this.numero = numero;
    }
}
