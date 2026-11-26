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
@Table(name = "telefonos", uniqueConstraints = {
        @UniqueConstraint(name = "uq_telefonos_contacto_numero", columnNames = {"id_contacto", "numero"})
})
@Check(name = "chk_telefonos_numero_no_vacio", constraints = "CHAR_LENGTH(TRIM(numero)) > 0")
public class Telefono {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_telefono", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_contacto", nullable = false,
            foreignKey = @ForeignKey(name = "fk_telefonos_contacto"))
    private Contacto contacto;

    @Column(name = "numero", nullable = false, length = 50)
    private String numero;

    public Telefono() {
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

    public String getNumero() {
        return numero;
    }

    public void setNumero(String numero) {
        this.numero = numero;
    }
}
