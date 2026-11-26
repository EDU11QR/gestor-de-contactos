package com.gestordecontactos.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
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
@AutoConfigureMockMvc
@Transactional
class ContactoListadoTests {

    private static final AtomicInteger SECUENCIA = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void listadoRequiereAutenticacion() throws Exception {
        mockMvc.perform(get("/contactos"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void listadoMuestraSoloLasColumnasPrevistasYEnlaceAlDetalle() throws Exception {
        String dni = dni("1000");
        Long id = insertarContacto("Ana", "Perez", dni, "CLIENTE");

        mockMvc.perform(get("/contactos").with(usuario()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ana")))
                .andExpect(content().string(containsString("Perez")))
                .andExpect(content().string(containsString(dni)))
                .andExpect(content().string(containsString("CLIENTE")))
                .andExpect(content().string(containsString("/contactos/" + id)))
                .andExpect(content().string(not(containsString("Teléfono"))))
                .andExpect(content().string(not(containsString("Correo electrónico"))));
    }

    @Test
    void buscaPorNombresInsensibleAMayusculas() throws Exception {
        insertarContacto("Ana", "Perez", dni("1100"), "CLIENTE");
        insertarContacto("Luis", "Gomez", dni("1100"), "PROVEEDOR");

        mockMvc.perform(get("/contactos").param("termino", "ANA").with(usuario()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Perez")))
                .andExpect(content().string(not(containsString("Gomez"))));
    }

    @Test
    void buscaPorApellidosInsensibleAMayusculas() throws Exception {
        insertarContacto("Ana", "Perez", dni("1200"), "CLIENTE");
        insertarContacto("Luis", "Gomez", dni("1200"), "PROVEEDOR");

        mockMvc.perform(get("/contactos").param("termino", "PEREZ").with(usuario()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ana")))
                .andExpect(content().string(not(containsString("Luis"))));
    }

    @Test
    void buscaPorDniParcial() throws Exception {
        String dniAna = dni("1300");
        insertarContacto("Ana", "Perez", dniAna, "CLIENTE");
        insertarContacto("Luis", "Gomez", dni("1400"), "PROVEEDOR");

        mockMvc.perform(get("/contactos").param("termino", dniAna.substring(0, 4)).with(usuario()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ana")))
                .andExpect(content().string(not(containsString("Gomez"))));
    }

    @Test
    void filtrosRespetanLaReglaDeAmbos() throws Exception {
        insertarContacto("Ana", "Perez", dni("2100"), "CLIENTE");
        insertarContacto("Luis", "Gomez", dni("2200"), "PROVEEDOR");
        insertarContacto("Marta", "Ruiz", dni("2300"), "AMBOS");

        mockMvc.perform(get("/contactos").param("tipo", "CLIENTE").with(usuario()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ana")))
                .andExpect(content().string(containsString("Marta")))
                .andExpect(content().string(not(containsString("Luis"))));

        mockMvc.perform(get("/contactos").param("tipo", "PROVEEDOR").with(usuario()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Luis")))
                .andExpect(content().string(containsString("Marta")))
                .andExpect(content().string(not(containsString("Ana"))));

        mockMvc.perform(get("/contactos").param("tipo", "AMBOS").with(usuario()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Marta")))
                .andExpect(content().string(not(containsString("Ana"))))
                .andExpect(content().string(not(containsString("Luis"))));
    }

    @Test
    void combinacionDeBusquedaYFiltroNoOmiteLaClasificacion() throws Exception {
        insertarContacto("Marta", "Perez", dni("3100"), "CLIENTE");
        insertarContacto("Marta", "Ruiz", dni("3200"), "AMBOS");

        mockMvc.perform(get("/contactos")
                        .param("termino", "Marta")
                        .param("tipo", "PROVEEDOR")
                        .with(usuario()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ruiz")))
                .andExpect(content().string(not(containsString("Perez"))));
    }

    @Test
    void paginacionConservaFiltrosYTamanio() throws Exception {
        for (int indice = 0; indice < 12; indice++) {
            insertarContacto("Pagina", String.format(Locale.ROOT, "P%02d", indice),
                    dni("4000"), "CLIENTE");
        }

        mockMvc.perform(get("/contactos")
                        .param("termino", "Pagina")
                        .param("tipo", "CLIENTE")
                        .param("size", "10")
                        .param("page", "0")
                        .with(usuario()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("P00")))
                .andExpect(content().string(not(containsString("P10"))))
                .andExpect(content().string(containsString("Página 1 de 2")))
                .andExpect(content().string(containsString("termino=Pagina")))
                .andExpect(content().string(containsString("tipo=CLIENTE")))
                .andExpect(content().string(containsString("size=10")));

        mockMvc.perform(get("/contactos")
                        .param("termino", "Pagina")
                        .param("tipo", "CLIENTE")
                        .param("size", "10")
                        .param("page", "1")
                        .with(usuario()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("P10")))
                .andExpect(content().string(containsString("P11")))
                .andExpect(content().string(containsString("Página 2 de 2")));
    }

    @Test
    void paginaNegativaYTamanoNoPermitidoSeNormalizan() throws Exception {
        for (int indice = 0; indice < 12; indice++) {
            insertarContacto("Pagina", String.format(Locale.ROOT, "P%02d", indice),
                    dni("5000"), "CLIENTE");
        }

        mockMvc.perform(get("/contactos")
                        .param("page", "-1")
                        .param("size", "999")
                        .with(usuario()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("P00")))
                .andExpect(content().string(containsString("Página 1 de 2")));
    }

    @Test
    void parametrosNoNumericosSeNormalizan() throws Exception {
        for (int indice = 0; indice < 12; indice++) {
            insertarContacto("Pagina", String.format(Locale.ROOT, "P%02d", indice),
                    dni("5500"), "CLIENTE");
        }

        mockMvc.perform(get("/contactos")
                        .param("page", "abc")
                        .param("size", "abc")
                        .with(usuario()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("P00")))
                .andExpect(content().string(not(containsString("P10"))))
                .andExpect(content().string(containsString("Página 1 de 2")));
    }

    @Test
    void resultadoVacioMuestraMensajeControlado() throws Exception {
        mockMvc.perform(get("/contactos").param("termino", "noexiste").with(usuario()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("No se encontraron contactos.")));
    }

    @Test
    void terminoDemasiadoLargoMuestraErrorControlado() throws Exception {
        mockMvc.perform(get("/contactos").param("termino", "a".repeat(101)).with(usuario()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "El término de búsqueda no puede superar 100 caracteres")));
    }

    @Test
    void detalleExistenteMuestraEnlaceAlListado() throws Exception {
        String dni = dni("6000");
        Long id = insertarContacto("Ana", "Perez", dni, "CLIENTE");

        mockMvc.perform(get("/contactos/" + id).with(usuario()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ana")))
                .andExpect(content().string(containsString(dni)))
                .andExpect(content().string(containsString("Volver al listado")));
    }

    @Test
    void detalleInexistenteMuestra404Controlado() throws Exception {
        mockMvc.perform(get("/contactos/999999999").with(usuario()))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("Contacto no encontrado")))
                .andExpect(content().string(containsString("Volver al listado")))
                .andExpect(content().string(not(containsString("org.springframework"))));
    }

    private Long insertarContacto(String nombres, String apellidos, String dni, String tipo) {
        jdbcTemplate.update(
                "INSERT INTO contactos "
                        + "(dni, tipo_contacto, nombres, apellidos, foto_archivo, "
                        + "foto_content_type, foto_tamano_bytes) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                dni, tipo, nombres, apellidos, UUID.randomUUID() + ".png", "image/png", 1024);
        return jdbcTemplate.queryForObject(
                "SELECT id_contacto FROM contactos WHERE dni = ?", Long.class, dni);
    }

    private static UserRequestPostProcessor usuario() {
        return user("prueba").roles("USER");
    }

    private String dni(String prefijo) {
        int sufijo = Math.floorMod(SECUENCIA.incrementAndGet(), 10_000);
        return prefijo + String.format(Locale.ROOT, "%04d", sufijo);
    }
}
