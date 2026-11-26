package com.gestordecontactos.repository;

import com.gestordecontactos.entity.Contacto;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContactoRepository extends JpaRepository<Contacto, Long> {

    boolean existsByDni(String dni);
}
