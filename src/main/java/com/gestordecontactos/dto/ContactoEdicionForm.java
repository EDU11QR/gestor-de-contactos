package com.gestordecontactos.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public class ContactoEdicionForm {

    @NotBlank(message = "Los nombres son obligatorios")
    @Size(max = 255, message = "Los nombres no pueden superar 255 caracteres")
    private String nombres;

    @NotBlank(message = "Los apellidos son obligatorios")
    @Size(max = 255, message = "Los apellidos no pueden superar 255 caracteres")
    private String apellidos;

    @NotBlank(message = "El DNI es obligatorio")
    @Pattern(regexp = "^\\d{8}$", message = "El DNI debe tener exactamente 8 dígitos")
    private String dni;

    @NotNull(message = "El tipo de contacto es obligatorio")
    private TipoContacto tipoContacto;

    @NotEmpty(message = "Debe registrar al menos un teléfono")
    @Valid
    private List<TelefonoForm> telefonos = new ArrayList<>();

    @NotEmpty(message = "Debe registrar al menos un correo electrónico")
    @Valid
    private List<CorreoForm> correosElectronicos = new ArrayList<>();

    private MultipartFile foto;

    public String getNombres() {
        return nombres;
    }

    public void setNombres(String nombres) {
        this.nombres = nombres;
    }

    public String getApellidos() {
        return apellidos;
    }

    public void setApellidos(String apellidos) {
        this.apellidos = apellidos;
    }

    public String getDni() {
        return dni;
    }

    public void setDni(String dni) {
        this.dni = dni;
    }

    public TipoContacto getTipoContacto() {
        return tipoContacto;
    }

    public void setTipoContacto(TipoContacto tipoContacto) {
        this.tipoContacto = tipoContacto;
    }

    public List<TelefonoForm> getTelefonos() {
        return telefonos;
    }

    public void setTelefonos(List<TelefonoForm> telefonos) {
        this.telefonos = telefonos;
    }

    public List<CorreoForm> getCorreosElectronicos() {
        return correosElectronicos;
    }

    public void setCorreosElectronicos(List<CorreoForm> correosElectronicos) {
        this.correosElectronicos = correosElectronicos;
    }

    public MultipartFile getFoto() {
        return foto;
    }

    public void setFoto(MultipartFile foto) {
        this.foto = foto;
    }
}
