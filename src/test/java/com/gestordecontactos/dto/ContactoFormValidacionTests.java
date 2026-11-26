package com.gestordecontactos.dto;

import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class ContactoFormValidacionTests {

    private static ValidatorFactory fabrica;
    private static Validator validador;

    @BeforeAll
    static void inicializar() {
        fabrica = Validation.buildDefaultValidatorFactory();
        validador = fabrica.getValidator();
    }

    @AfterAll
    static void cerrar() {
        fabrica.close();
    }

    @Test
    void formularioValidoNoGeneraViolaciones() {
        assertTrue(validador.validate(formulario("987654321", "ana@ejemplo.com")).isEmpty());
    }

    @Test
    void aceptaTelefonoPeruanoConYSinPrefijo() {
        assertTrue(validador.validate(formulario("+51987654321", "ana@ejemplo.com")).isEmpty());
        assertTrue(validador.validate(formulario("51987654321", "ana@ejemplo.com")).isEmpty());
    }

    @Test
    void dniDebeTenerOchoDigitos() {
        ContactoForm form = formulario("987654321", "ana@ejemplo.com");
        form.setDni("1234567");
        assertTrue(tieneViolacion(form, "dni"));
    }

    @Test
    void telefonoExtranjeroEsRechazado() {
        assertTrue(tieneViolacion(formulario("+59998765432", "ana@ejemplo.com"), "telefonos[0].numero"));
    }

    @Test
    void correoInvalidoEsRechazado() {
        assertTrue(tieneViolacion(formulario("987654321", "correo-invalido"), "correosElectronicos[0].direccion"));
    }

    @Test
    void listasDeTelefonosYCorreosNoPuedenEstarVacias() {
        ContactoForm form = formulario("987654321", "ana@ejemplo.com");
        form.setTelefonos(List.of());
        form.setCorreosElectronicos(List.of());
        Set<ConstraintViolation<ContactoForm>> violaciones = validador.validate(form);
        assertTrue(violaciones.stream().anyMatch(v -> v.getPropertyPath().toString().equals("telefonos")));
        assertTrue(violaciones.stream().anyMatch(v -> v.getPropertyPath().toString().equals("correosElectronicos")));
    }

    @Test
    void fotografíaEsObligatoria() {
        ContactoForm form = formulario("987654321", "ana@ejemplo.com");
        form.setFoto(null);
        assertTrue(tieneViolacion(form, "foto"));
    }

    private boolean tieneViolacion(ContactoForm form, String propiedad) {
        return validador.validate(form).stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals(propiedad));
    }

    private ContactoForm formulario(String telefono, String correo) {
        ContactoForm form = new ContactoForm();
        form.setNombres("Ana");
        form.setApellidos("Pérez");
        form.setDni("12345678");
        form.setTipoContacto(TipoContacto.CLIENTE);
        form.setTelefonos(List.of(new TelefonoForm(telefono)));
        form.setCorreosElectronicos(List.of(new CorreoForm(correo)));
        form.setFoto(new MockMultipartFile("foto", "foto.png", "image/png", new byte[] {1}));
        return form;
    }
}
