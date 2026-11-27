package com.gestordecontactos.dto;

import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import org.junit.jupiter.api.Test;

class FormulariosContactoValidacionTests {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void altaRechazaMasDeVeinteTelefonos() {
        ContactoForm form = new ContactoForm();
        for (int indice = 0; indice < 21; indice += 1) {
            form.getTelefonos().add(new TelefonoForm("+51987654321"));
        }

        assertTrue(tieneViolacion(form, "telefonos"));
    }

    @Test
    void altaRechazaMasDeVeinteCorreos() {
        ContactoForm form = new ContactoForm();
        for (int indice = 0; indice < 21; indice += 1) {
            form.getCorreosElectronicos().add(new CorreoForm("ana@ejemplo.com"));
        }

        assertTrue(tieneViolacion(form, "correosElectronicos"));
    }

    @Test
    void edicionRechazaMasDeVeinteTelefonos() {
        ContactoEdicionForm form = new ContactoEdicionForm();
        for (int indice = 0; indice < 21; indice += 1) {
            form.getTelefonos().add(new TelefonoForm("+51987654321"));
        }

        assertTrue(tieneViolacion(form, "telefonos"));
    }

    @Test
    void edicionRechazaMasDeVeinteCorreos() {
        ContactoEdicionForm form = new ContactoEdicionForm();
        for (int indice = 0; indice < 21; indice += 1) {
            form.getCorreosElectronicos().add(new CorreoForm("ana@ejemplo.com"));
        }

        assertTrue(tieneViolacion(form, "correosElectronicos"));
    }

    private boolean tieneViolacion(Object form, String propiedad) {
        Set<ConstraintViolation<Object>> violaciones = validator.validate(form);
        return violaciones.stream()
                .anyMatch(violacion -> violacion.getPropertyPath().toString().equals(propiedad));
    }
}
