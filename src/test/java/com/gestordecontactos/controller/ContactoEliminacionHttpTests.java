package com.gestordecontactos.controller;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gestordecontactos.repository.ConfirmacionEliminacionRepository;
import com.gestordecontactos.service.ContactoService;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import java.net.URI;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
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
@ContextConfiguration(initializers = ContactoEliminacionHttpTests.VerificarBaseDeDatosDePrueba.class)
@AutoConfigureMockMvc
@Transactional
class ContactoEliminacionHttpTests {

    private static final AtomicInteger SECUENCIA = new AtomicInteger();
    private static final String COOKIE_SESION = "GESTOR_SESSION";
    private static final String MENSAJE_ERROR_GENERICO =
            "No se pudo completar la eliminación. Inténtelo nuevamente.";
    private static final Pattern TOKEN_OCULTO =
            Pattern.compile("name=\"token\"[^>]*value=\"([^\"]+)\"");
    private static final Pattern TOKEN_BASE64_URL = Pattern.compile("[A-Za-z0-9_-]{43}");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @MockitoBean
    private ConfirmacionEliminacionRepository confirmacionEliminacionRepository;

    @MockitoSpyBean
    private ContactoService contactoService;

    private final Map<String, EstadoConfirmacion> confirmaciones = new HashMap<>();

    @BeforeEach
    void configurarConfirmaciones() {
        confirmaciones.clear();
        clearInvocations(contactoService);

        doAnswer(invocation -> {
            confirmaciones.put(invocation.getArgument(0, String.class),
                    new EstadoConfirmacion(
                            invocation.getArgument(1, String.class),
                            invocation.getArgument(2, Long.class)));
            return null;
        }).when(confirmacionEliminacionRepository)
                .reemplazar(anyString(), anyString(), anyLong(), anyLong());

        when(confirmacionEliminacionRepository.consumir(anyString(), anyString(), anyLong()))
                .thenAnswer(invocation -> {
                    String idSesion = invocation.getArgument(0, String.class);
                    String tokenHash = invocation.getArgument(1, String.class);
                    Long idContacto = invocation.getArgument(2, Long.class);
                    EstadoConfirmacion estado = confirmaciones.get(idSesion);
                    if (estado == null || estado.consumida
                            || !estado.tokenHash.equals(tokenHash)
                            || !estado.idContacto.equals(idContacto)) {
                        return 0;
                    }
                    estado.consumida = true;
                    return 1;
                });
    }

    @Test
    void confirmacionGeneraTokenBase64UrlYLoEntregaAVista() throws Exception {
        ContactoDePrueba contacto = insertarContacto("Ana", "Pérez", "CLIENTE");

        Confirmacion confirmacion = confirmar(contacto.id(), null);

        assertTrue(confirmacion.html().contains("Confirmar eliminación"));
        assertTrue(TOKEN_BASE64_URL.matcher(confirmacion.token()).matches());
        verify(confirmacionEliminacionRepository)
                .reemplazar(anyString(), anyString(), eq(contacto.id()), eq(10L));
    }

    @Test
    void tokenQuedaAsociadoAlIdCorrecto() throws Exception {
        ContactoDePrueba primero = insertarContacto("Ana", "Pérez", "CLIENTE");
        ContactoDePrueba segundo = insertarContacto("Luis", "Gómez", "PROVEEDOR");
        Confirmacion confirmacion = confirmar(primero.id(), null);

        mockMvc.perform(postEliminar(primero.id(), confirmacion.token(), confirmacion.cookieSesion()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/contactos"));

        assertEquals(0, contarContacto(primero.id()));
        assertEquals(1, contarContacto(segundo.id()));
    }

    @Test
    void vistaNoExponeRutaInternaDeLaFotografia() throws Exception {
        ContactoDePrueba contacto = insertarContacto("Ana", "Pérez", "AMBOS");

        Confirmacion confirmacion = confirmar(contacto.id(), null);

        assertTrue(confirmacion.html().contains("Ana"));
        assertTrue(confirmacion.html().contains("Pérez"));
        assertTrue(confirmacion.html().contains(contacto.dni()));
        assertTrue(confirmacion.html().contains("AMBOS"));
        assertFalse(confirmacion.html().contains(contacto.fotoArchivo()));
        assertFalse(confirmacion.html().contains("foto_archivo"));
    }

    @Test
    void postValidoConsumeTokenYEliminaContacto() throws Exception {
        ContactoDePrueba contacto = insertarContacto("Ana", "Pérez", "CLIENTE");
        Confirmacion confirmacion = confirmar(contacto.id(), null);

        mockMvc.perform(postEliminar(contacto.id(), confirmacion.token(), confirmacion.cookieSesion()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/contactos"))
                .andExpect(flash().attribute("eliminado", true));

        verify(confirmacionEliminacionRepository)
                .consumir(anyString(), anyString(), eq(contacto.id()));
        verify(contactoService).eliminar(contacto.id());
        assertEquals(0, contarContacto(contacto.id()));

        mockMvc.perform(get("/contactos").with(usuario()).cookie(confirmacion.cookieSesion()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Contacto eliminado correctamente.")));
    }

    @Test
    void tokenNoPuedeReutilizarse() throws Exception {
        ContactoDePrueba contacto = insertarContacto("Ana", "Pérez", "CLIENTE");
        Confirmacion confirmacion = confirmar(contacto.id(), null);

        mockMvc.perform(postEliminar(contacto.id(), confirmacion.token(), confirmacion.cookieSesion()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/contactos"));

        mockMvc.perform(postEliminar(contacto.id(), confirmacion.token(), confirmacion.cookieSesion()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/contactos"))
                .andExpect(flash().attribute("errorEliminacion", MENSAJE_ERROR_GENERICO));

        verify(confirmacionEliminacionRepository, times(2))
                .consumir(anyString(), anyString(), eq(contacto.id()));
        verify(contactoService, times(1)).eliminar(contacto.id());
        assertEquals(0, contarContacto(contacto.id()));
    }

    @Test
    void tokenIncorrectoNoConsumeNiInvocaServicio() throws Exception {
        ContactoDePrueba contacto = insertarContacto("Ana", "Pérez", "CLIENTE");
        Confirmacion confirmacion = confirmar(contacto.id(), null);

        mockMvc.perform(postEliminar(contacto.id(),
                        confirmacion.token() + "-incorrecto", confirmacion.cookieSesion()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/contactos"))
                .andExpect(flash().attribute("errorEliminacion", MENSAJE_ERROR_GENERICO));

        verify(confirmacionEliminacionRepository, never())
                .consumir(anyString(), anyString(), anyLong());
        verify(contactoService, never()).eliminar(anyLong());
        assertEquals(1, contarContacto(contacto.id()));
    }

    @Test
    void tokenAsociadoAOtroContactoNoFunciona() throws Exception {
        ContactoDePrueba primero = insertarContacto("Ana", "Pérez", "CLIENTE");
        ContactoDePrueba segundo = insertarContacto("Luis", "Gómez", "PROVEEDOR");
        Confirmacion confirmacion = confirmar(primero.id(), null);

        mockMvc.perform(postEliminar(segundo.id(), confirmacion.token(), confirmacion.cookieSesion()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/contactos"))
                .andExpect(flash().attribute("errorEliminacion", MENSAJE_ERROR_GENERICO));

        verify(contactoService, never()).eliminar(segundo.id());
        assertEquals(1, contarContacto(primero.id()));
        assertEquals(1, contarContacto(segundo.id()));
    }

    @Test
    void tokenDeOtraSesionNoFunciona() throws Exception {
        ContactoDePrueba contacto = insertarContacto("Ana", "Pérez", "CLIENTE");
        Confirmacion primera = confirmar(contacto.id(), null);
        Confirmacion segunda = confirmar(contacto.id(), null);

        assertNotEquals(primera.cookieSesion().getValue(), segunda.cookieSesion().getValue());
        assertNotEquals(primera.token(), segunda.token());

        mockMvc.perform(postEliminar(contacto.id(), primera.token(), segunda.cookieSesion()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/contactos"))
                .andExpect(flash().attribute("errorEliminacion", MENSAJE_ERROR_GENERICO));

        verify(contactoService, never()).eliminar(contacto.id());
        assertEquals(1, contarContacto(contacto.id()));
    }

    @Test
    void nuevaConfirmacionReemplazaTokenAnterior() throws Exception {
        ContactoDePrueba contacto = insertarContacto("Ana", "Pérez", "CLIENTE");
        Confirmacion primera = confirmar(contacto.id(), null);
        Confirmacion segunda = confirmar(contacto.id(), primera.cookieSesion());

        assertNotEquals(primera.token(), segunda.token());

        mockMvc.perform(postEliminar(contacto.id(), primera.token(), segunda.cookieSesion()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/contactos"))
                .andExpect(flash().attribute("errorEliminacion", MENSAJE_ERROR_GENERICO));
        assertEquals(1, contarContacto(contacto.id()));

        mockMvc.perform(postEliminar(contacto.id(), segunda.token(), segunda.cookieSesion()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/contactos"));
        assertEquals(0, contarContacto(contacto.id()));
    }

    @Test
    void postSinCsrfEsRechazado() throws Exception {
        ContactoDePrueba contacto = insertarContacto("Ana", "Pérez", "CLIENTE");
        Confirmacion confirmacion = confirmar(contacto.id(), null);

        mockMvc.perform(post("/contactos/{id}/eliminar", contacto.id())
                        .param("token", confirmacion.token())
                        .with(usuario())
                        .cookie(confirmacion.cookieSesion()))
                .andExpect(status().isForbidden());

        assertEquals(1, contarContacto(contacto.id()));
    }

    @Test
    void usuarioNoAutenticadoNoPuedeAccederNiEjecutarEliminacion() throws Exception {
        ContactoDePrueba contacto = insertarContacto("Ana", "Pérez", "CLIENTE");

        mockMvc.perform(get("/contactos/{id}/eliminar", contacto.id()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));

        mockMvc.perform(post("/contactos/{id}/eliminar", contacto.id())
                        .param("token", UUID.randomUUID().toString())
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));

        assertEquals(1, contarContacto(contacto.id()));
    }

    @Test
    void contactoInexistenteNoRevelaExistenciaEnGetYPost() throws Exception {
        Long idInexistente = Long.MAX_VALUE;

        mockMvc.perform(get("/contactos/{id}/eliminar", idInexistente).with(usuario()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/contactos"))
                .andExpect(flash().attribute("errorEliminacion", MENSAJE_ERROR_GENERICO));

        ContactoDePrueba contacto = insertarContacto("Ana", "Pérez", "CLIENTE");
        Confirmacion confirmacion = confirmar(contacto.id(), null);
        jdbcTemplate.update("DELETE FROM contactos WHERE id_contacto = ?", contacto.id());
        entityManager.clear();

        mockMvc.perform(postEliminar(contacto.id(), confirmacion.token(), confirmacion.cookieSesion()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/contactos"))
                .andExpect(flash().attribute("errorEliminacion", MENSAJE_ERROR_GENERICO));
    }

    @Test
    void falloPersistenciaAlGenerarMuestraMensajeGenerico() throws Exception {
        ContactoDePrueba contacto = insertarContacto("Ana", "Pérez", "CLIENTE");
        doThrow(new DataRetrievalFailureException("FALLO_PERSISTENCIA_BD"))
                .when(confirmacionEliminacionRepository)
                .reemplazar(anyString(), anyString(), anyLong(), anyLong());

        mockMvc.perform(get("/contactos/{id}/eliminar", contacto.id()).with(usuario()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/contactos"))
                .andExpect(flash().attribute("errorEliminacion", MENSAJE_ERROR_GENERICO));

        verify(contactoService, never()).eliminar(anyLong());
    }

    @Test
    void falloPersistenciaAlConsumirNoEliminaYMuestraMensajeGenerico() throws Exception {
        ContactoDePrueba contacto = insertarContacto("Ana", "Pérez", "CLIENTE");
        Confirmacion confirmacion = confirmar(contacto.id(), null);
        when(confirmacionEliminacionRepository.consumir(anyString(), anyString(), anyLong()))
                .thenThrow(new DataRetrievalFailureException("FALLO_PERSISTENCIA_BD"));

        mockMvc.perform(postEliminar(contacto.id(), confirmacion.token(), confirmacion.cookieSesion()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/contactos"))
                .andExpect(flash().attribute("errorEliminacion", MENSAJE_ERROR_GENERICO));

        verify(contactoService, never()).eliminar(anyLong());
        assertEquals(1, contarContacto(contacto.id()));
    }

    @Test
    void falloAlEliminarMuestraMensajeGenericoSinDetallesTecnicos() throws Exception {
        ContactoDePrueba contacto = insertarContacto("Ana", "Pérez", "CLIENTE");
        Confirmacion confirmacion = confirmar(contacto.id(), null);
        doThrow(new IllegalStateException("DETALLE_TECNICO_SENSIBLE"))
                .when(contactoService).eliminar(contacto.id());

        mockMvc.perform(postEliminar(contacto.id(), confirmacion.token(), confirmacion.cookieSesion()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/contactos"))
                .andExpect(flash().attribute("errorEliminacion", MENSAJE_ERROR_GENERICO));

        verify(confirmacionEliminacionRepository)
                .consumir(anyString(), anyString(), eq(contacto.id()));

        String html = mockMvc.perform(
                        get("/contactos").with(usuario()).cookie(confirmacion.cookieSesion()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertTrue(html.contains(MENSAJE_ERROR_GENERICO));
        assertFalse(html.contains("DETALLE_TECNICO_SENSIBLE"));
    }

    private Confirmacion confirmar(Long id, Cookie cookieSesion) throws Exception {
        MockHttpServletRequestBuilder solicitud = get("/contactos/{id}/eliminar", id).with(usuario());
        if (cookieSesion != null) {
            solicitud.cookie(cookieSesion);
        }

        MvcResult resultado = mockMvc.perform(solicitud)
                .andExpect(status().isOk())
                .andReturn();

        Cookie cookieResultado = resultado.getResponse().getCookie(COOKIE_SESION);
        assertNotNull(cookieResultado != null ? cookieResultado : cookieSesion);
        return new Confirmacion(tokenOculto(resultado.getResponse().getContentAsString()),
                cookieResultado != null ? cookieResultado : cookieSesion,
                resultado.getResponse().getContentAsString());
    }

    private MockHttpServletRequestBuilder postEliminar(Long id, String token, Cookie cookieSesion) {
        return post("/contactos/{id}/eliminar", id)
                .param("token", token)
                .with(usuario())
                .with(csrf())
                .cookie(cookieSesion);
    }

    private String tokenOculto(String html) {
        Matcher coincidencia = TOKEN_OCULTO.matcher(html);
        assertTrue(coincidencia.find(), "El formulario debe incluir el token oculto");
        return coincidencia.group(1);
    }

    private int contarContacto(Long id) {
        entityManager.flush();
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM contactos WHERE id_contacto = ?", Integer.class, id);
    }

    private ContactoDePrueba insertarContacto(String nombres, String apellidos, String tipo) {
        String dni = dniNuevo();
        String fotoArchivo = UUID.randomUUID() + ".png";
        jdbcTemplate.update(
                "INSERT INTO contactos "
                        + "(dni, tipo_contacto, nombres, apellidos, foto_archivo, "
                        + "foto_content_type, foto_tamano_bytes) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                dni, tipo, nombres, apellidos, fotoArchivo, "image/png", 1024);
        Long id = jdbcTemplate.queryForObject(
                "SELECT id_contacto FROM contactos WHERE dni = ?", Long.class, dni);
        assertNotNull(id);
        return new ContactoDePrueba(id, dni, fotoArchivo);
    }

    private static UserRequestPostProcessor usuario() {
        return user("prueba").roles("USER");
    }

    private static String dniNuevo() {
        long valor = Math.floorMod(System.nanoTime() + SECUENCIA.incrementAndGet(), 100_000_000L);
        return String.format(Locale.ROOT, "%08d", valor);
    }

    private static final class EstadoConfirmacion {

        private final String tokenHash;
        private final Long idContacto;
        private boolean consumida;

        private EstadoConfirmacion(String tokenHash, Long idContacto) {
            this.tokenHash = tokenHash;
            this.idContacto = idContacto;
        }
    }

    private record ContactoDePrueba(Long id, String dni, String fotoArchivo) {
    }

    private record Confirmacion(String token, Cookie cookieSesion, String html) {
    }

    public static class VerificarBaseDeDatosDePrueba
            implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        @Override
        public void initialize(ConfigurableApplicationContext applicationContext) {
            String url = applicationContext.getEnvironment().getProperty("spring.datasource.url");
            assertNotNull(url, "SPRING_DATASOURCE_URL es obligatoria");
            String uri = url.startsWith("jdbc:") ? url.substring("jdbc:".length()) : url;
            String ruta = URI.create(uri).getPath();
            assertNotNull(ruta, "SPRING_DATASOURCE_URL debe incluir el nombre de la base de datos");
            String nombreBase = ruta.startsWith("/") ? ruta.substring(1) : ruta;
            int finNombreBase = nombreBase.indexOf('/');
            if (finNombreBase >= 0) {
                nombreBase = nombreBase.substring(0, finNombreBase);
            }
            assertEquals("gestor_contactos_test", nombreBase,
                    "La prueba solo puede ejecutarse contra gestor_contactos_test");
            assertNotNull(applicationContext.getEnvironment().getProperty("spring.datasource.username"));
            assertNotNull(applicationContext.getEnvironment().getProperty("spring.datasource.password"));
        }
    }
}
