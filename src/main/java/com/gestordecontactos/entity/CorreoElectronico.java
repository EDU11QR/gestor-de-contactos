package com.gestordecontactos.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.Check;

@Entity
@Table(name = "correos_electronicos", uniqueConstraints = {
        @UniqueConstraint(name = "uq_correos_contacto_direccion", columnNames = {"id_contacto", "direccion"})
})
@Check(name = "chk_correos_direccion_no_vacio", constraints = "CHAR_LENGTH(TRIM(direccion)) > 0")
public class CorreoElectronico {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_correo", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_contacto", nullable = false,
            foreignKey = @ForeignKey(name = "fk_correos_contacto"))
    private Contacto contacto;

    @Column(name = "direccion", nullable = false, length = 255)
    private String direccion;

    public CorreoElectronico() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Contacto getContacto() {
        return contacto;
    }

    public void setContacto(Contacto contacto) {
        this.contacto = contacto;
    }

    public String getDireccion() {
        return direccion;
    }

    public void setDireccion(String direccion) {
        this.direccion = direccion;
    }
}
