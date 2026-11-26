package com.gestordecontactos.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "contactos", uniqueConstraints = {
        @UniqueConstraint(name = "uq_contactos_dni", columnNames = "dni"),
        @UniqueConstraint(name = "uq_contactos_foto_archivo", columnNames = "foto_archivo")
})
@Check(name = "chk_contactos_dni_digitos", constraints = "REGEXP_LIKE(dni, _ascii'^[0-9]{8}$')")
@Check(name = "chk_contactos_tipo_contacto", constraints = "tipo_contacto IN ('CLIENTE', 'PROVEEDOR', 'AMBOS')")
@Check(name = "chk_contactos_nombres_no_vacio", constraints = "CHAR_LENGTH(TRIM(nombres)) > 0")
@Check(name = "chk_contactos_apellidos_no_vacio", constraints = "CHAR_LENGTH(TRIM(apellidos)) > 0")
@Check(name = "chk_contactos_foto_content_type", constraints = "foto_content_type IN ('image/jpeg', 'image/png')")
@Check(name = "chk_contactos_foto_tamano_bytes", constraints = "foto_tamano_bytes BETWEEN 1 AND 2097152")
public class Contacto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_contacto", nullable = false)
    private Long id;

    @Column(name = "dni", nullable = false, length = 8,
            columnDefinition = "CHAR(8) CHARACTER SET ascii COLLATE ascii_bin")
    @JdbcTypeCode(SqlTypes.CHAR)
    private String dni;

    @Column(name = "tipo_contacto", nullable = false, length = 16,
            columnDefinition = "VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin")
    private String tipoContacto;

    @Column(name = "nombres", nullable = false, length = 255)
    private String nombres;

    @Column(name = "apellidos", nullable = false, length = 255)
    private String apellidos;

    @Column(name = "foto_archivo", nullable = false, length = 255,
            columnDefinition = "VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin")
    private String fotoArchivo;

    @Column(name = "foto_content_type", nullable = false, length = 64,
            columnDefinition = "VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin")
    private String fotoContentType;

    @Column(name = "foto_tamano_bytes", nullable = false)
    private int fotoTamanoBytes;

    @OneToMany(mappedBy = "contacto", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Telefono> telefonos = new ArrayList<>();

    @OneToMany(mappedBy = "contacto", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CorreoElectronico> correosElectronicos = new ArrayList<>();

    public Contacto() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDni() {
        return dni;
    }

    public void setDni(String dni) {
        this.dni = dni;
    }

    public String getTipoContacto() {
        return tipoContacto;
    }

    public void setTipoContacto(String tipoContacto) {
        this.tipoContacto = tipoContacto;
    }

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

    public String getFotoArchivo() {
        return fotoArchivo;
    }

    public void setFotoArchivo(String fotoArchivo) {
        this.fotoArchivo = fotoArchivo;
    }

    public String getFotoContentType() {
        return fotoContentType;
    }

    public void setFotoContentType(String fotoContentType) {
        this.fotoContentType = fotoContentType;
    }

    public int getFotoTamanoBytes() {
        return fotoTamanoBytes;
    }

    public void setFotoTamanoBytes(int fotoTamanoBytes) {
        this.fotoTamanoBytes = fotoTamanoBytes;
    }

    public List<Telefono> getTelefonos() {
        return telefonos;
    }

    public List<CorreoElectronico> getCorreosElectronicos() {
        return correosElectronicos;
    }

    public void addTelefono(Telefono telefono) {
        telefonos.add(telefono);
        telefono.setContacto(this);
    }

    public void addCorreoElectronico(CorreoElectronico correoElectronico) {
        correosElectronicos.add(correoElectronico);
        correoElectronico.setContacto(this);
    }
}
