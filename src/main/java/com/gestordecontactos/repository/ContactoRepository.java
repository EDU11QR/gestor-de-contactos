package com.gestordecontactos.repository;

import com.gestordecontactos.entity.Contacto;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ContactoRepository extends JpaRepository<Contacto, Long> {

    boolean existsByDni(String dni);

    boolean existsByDniAndIdNot(String dni, Long id);

    boolean existsByFotoArchivo(String fotoArchivo);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Contacto c where c.id = :id")
    Optional<Contacto> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            select c from Contacto c
            where (
                :terminoVacio = true
                or lower(c.nombres) like lower(:patron) escape '\\'
                or lower(c.apellidos) like lower(:patron) escape '\\'
                or c.dni like :patron escape '\\'
            )
            and (
                :tipo is null
                or c.tipoContacto = :tipo
                or (:tipo = 'CLIENTE' and c.tipoContacto = 'AMBOS')
                or (:tipo = 'PROVEEDOR' and c.tipoContacto = 'AMBOS')
            )
            """)
    Page<Contacto> buscarContactos(@Param("terminoVacio") boolean terminoVacio,
            @Param("patron") String patron,
            @Param("tipo") String tipo,
            Pageable pageable);
}
