package com.gestordecontactos.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.gestordecontactos.controller.ContactoRegistroTests;
import com.gestordecontactos.entity.Contacto;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
        "spring.datasource.url=${SPRING_DATASOURCE_URL}",
        "spring.datasource.username=${SPRING_DATASOURCE_USERNAME}",
        "spring.datasource.password=${SPRING_DATASOURCE_PASSWORD}",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.session.store-type=jdbc",
        "spring.session.jdbc.table-name=SPRING_SESSION",
        "spring.session.jdbc.initialize-schema=never",
        "app.fotos.dir=${java.io.tmpdir}/gestor-contactos-test-fotos",
        "app.provisioning.email=",
        "app.provisioning.password=",
        "app.provisioning.nombre="
})
@ContextConfiguration(initializers = ContactoRegistroTests.VerificarBaseDeDatosDePrueba.class)
@Transactional
class ContactoRepositoryListadoTests {

    private static final AtomicInteger SECUENCIA = new AtomicInteger();

    @Autowired
    private ContactoRepository contactoRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void buscaPorNombresInsensibleAMayusculas() {
        guardar("Ana", "Perez", dni("1000"), "CLIENTE");
        guardar("Luis", "Gomez", dni("1000"), "PROVEEDOR");
        flushClear();

        Page<Contacto> pagina = contactoRepository.buscarContactos(false, "%ana%", null, pagina(0, 10));

        assertEquals(1, pagina.getTotalElements());
        assertEquals("Ana", pagina.getContent().get(0).getNombres());
    }

    @Test
    void buscaPorApellidosInsensibleAMayusculas() {
        guardar("Ana", "Perez", dni("1000"), "CLIENTE");
        guardar("Luis", "Gomez", dni("1000"), "PROVEEDOR");
        flushClear();

        Page<Contacto> pagina = contactoRepository.buscarContactos(false, "%gomez%", null, pagina(0, 10));

        assertEquals(1, pagina.getTotalElements());
        assertEquals("Gomez", pagina.getContent().get(0).getApellidos());
    }

    @Test
    void buscaPorDniParcial() {
        String dniBuscado = dni("2000");
        guardar("Ana", "Perez", dniBuscado, "CLIENTE");
        guardar("Luis", "Gomez", dni("2100"), "PROVEEDOR");
        flushClear();

        Page<Contacto> pagina = contactoRepository.buscarContactos(
                false, "%" + dniBuscado.substring(0, 4) + "%", null, pagina(0, 10));

        assertEquals(1, pagina.getTotalElements());
        assertEquals(dniBuscado, pagina.getContent().get(0).getDni());
    }

    @Test
    void filtroClienteIncluyeClientesYAmbos() {
        String marca = marcaUnica();
        guardar("Ana", marca + "Perez", dni("3000"), "CLIENTE");
        guardar("Luis", marca + "Gomez", dni("3000"), "PROVEEDOR");
        guardar("Marta", marca + "Ruiz", dni("3000"), "AMBOS");
        flushClear();

        Page<Contacto> pagina = contactoRepository.buscarContactos(
                false, "%" + marca + "%", "CLIENTE", pagina(0, 10));

        assertEquals(2, pagina.getTotalElements());
        assertEquals(List.of("CLIENTE", "AMBOS"), tipos(pagina));
    }

    @Test
    void filtroProveedorIncluyeProveedoresYAmbos() {
        String marca = marcaUnica();
        guardar("Ana", marca + "Perez", dni("3000"), "CLIENTE");
        guardar("Luis", marca + "Gomez", dni("3000"), "PROVEEDOR");
        guardar("Marta", marca + "Ruiz", dni("3000"), "AMBOS");
        flushClear();

        Page<Contacto> pagina = contactoRepository.buscarContactos(
                false, "%" + marca + "%", "PROVEEDOR", pagina(0, 10));

        assertEquals(2, pagina.getTotalElements());
        assertEquals(List.of("PROVEEDOR", "AMBOS"), tipos(pagina));
    }

    @Test
    void filtroAmbosExcluyeOtrosTipos() {
        String marca = marcaUnica();
        guardar("Ana", marca + "Perez", dni("3000"), "CLIENTE");
        guardar("Luis", marca + "Gomez", dni("3000"), "PROVEEDOR");
        guardar("Marta", marca + "Ruiz", dni("3000"), "AMBOS");
        flushClear();

        Page<Contacto> pagina = contactoRepository.buscarContactos(
                false, "%" + marca + "%", "AMBOS", pagina(0, 10));

        assertEquals(1, pagina.getTotalElements());
        assertEquals(List.of("AMBOS"), tipos(pagina));
    }

    @Test
    void combinacionDeBusquedaYFiltroRespetaLaClasificacion() {
        guardar("Marta", "Perez", dni("4000"), "CLIENTE");
        guardar("Marta", "Ruiz", dni("4000"), "AMBOS");
        flushClear();

        Page<Contacto> pagina = contactoRepository.buscarContactos(
                false, "%marta%", "PROVEEDOR", pagina(0, 10));

        assertEquals(1, pagina.getTotalElements());
        assertEquals("AMBOS", pagina.getContent().get(0).getTipoContacto());
        assertEquals("Ruiz", pagina.getContent().get(0).getApellidos());
    }

    @Test
    void buscaComodinesComoCaracteresLiterales() {
        guardar("Ana%", "Perez%", dni("7000"), "CLIENTE");
        guardar("Ana_", "Perez_", dni("7000"), "PROVEEDOR");
        guardar("Ana\\", "Perez\\", dni("7000"), "AMBOS");
        guardar("Ana", "Perez", dni("7000"), "CLIENTE");
        flushClear();

        Page<Contacto> porPorcentaje = contactoRepository.buscarContactos(
                false, "%\\%%", null, pagina(0, 10));
        assertEquals(1, porPorcentaje.getTotalElements());
        assertEquals("Ana%", porPorcentaje.getContent().get(0).getNombres());

        Page<Contacto> porGuionBajo = contactoRepository.buscarContactos(
                false, "%\\_%", null, pagina(0, 10));
        assertEquals(1, porGuionBajo.getTotalElements());
        assertEquals("Ana_", porGuionBajo.getContent().get(0).getNombres());

        Page<Contacto> porBarraInvertida = contactoRepository.buscarContactos(
                false, "%\\\\%", null, pagina(0, 10));
        assertEquals(1, porBarraInvertida.getTotalElements());
        assertEquals("Ana\\", porBarraInvertida.getContent().get(0).getNombres());
    }

    @Test
    void paginaLosResultadosConOrdenFijo() {
        for (int indice = 0; indice < 12; indice++) {
            guardar("Pagina", String.format(Locale.ROOT, "P%02d", indice), dni("5000"), "CLIENTE");
        }
        flushClear();

        Page<Contacto> primera = contactoRepository.buscarContactos(
                false, "%pagina%", "CLIENTE", pagina(0, 10));
        Page<Contacto> segunda = contactoRepository.buscarContactos(
                false, "%pagina%", "CLIENTE", pagina(1, 10));

        assertEquals(12, primera.getTotalElements());
        assertEquals(10, primera.getContent().size());
        assertEquals("P00", primera.getContent().get(0).getApellidos());
        assertEquals(2, segunda.getContent().size());
        assertTrue(segunda.getContent().stream().anyMatch(contacto -> "P10".equals(contacto.getApellidos())));
    }

    private void guardar(String nombres, String apellidos, String dni, String tipo) {
        Contacto contacto = new Contacto();
        contacto.setNombres(nombres);
        contacto.setApellidos(apellidos);
        contacto.setDni(dni);
        contacto.setTipoContacto(tipo);
        contacto.setFotoArchivo(UUID.randomUUID() + ".png");
        contacto.setFotoContentType("image/png");
        contacto.setFotoTamanoBytes(1024);
        contactoRepository.save(contacto);
    }

    private void flushClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private Pageable pagina(int page, int size) {
        return PageRequest.of(page, size,
                Sort.by("apellidos").ascending().and(Sort.by("nombres")).and(Sort.by("id")));
    }

    private List<String> tipos(Page<Contacto> pagina) {
        return pagina.getContent().stream().map(Contacto::getTipoContacto).toList();
    }

    private String dni(String prefijo) {
        int sufijo = Math.floorMod(SECUENCIA.incrementAndGet(), 10_000);
        return prefijo + String.format(Locale.ROOT, "%04d", sufijo);
    }

    private String marcaUnica() {
        return "Filtro" + UUID.randomUUID().toString().substring(0, 8);
    }
}
