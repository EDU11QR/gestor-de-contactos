package com.gestordecontactos;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gestordecontactos.dto.ContactoDetalleDto;
import com.gestordecontactos.dto.ContactoEdicionForm;
import com.gestordecontactos.dto.CorreoForm;
import com.gestordecontactos.dto.TelefonoForm;
import com.gestordecontactos.dto.TipoContacto;
import com.gestordecontactos.repository.ConfirmacionEliminacionRepository;
import com.gestordecontactos.repository.ContactoRepository;
import com.gestordecontactos.repository.SesionRevocacionJdbcRepository;
import com.gestordecontactos.repository.SesionRollbackJdbcRepository;
import com.gestordecontactos.repository.UsuarioRepository;
import com.gestordecontactos.service.ContactoService;
import com.gestordecontactos.service.EliminacionContactoService;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.session.SessionAutoConfiguration",
        "app.fotos.dir=${java.io.tmpdir}/gestor-contactos-test-fotos",
        "app.provisioning.email=",
        "app.provisioning.password=",
        "app.provisioning.nombre=",
        "app.revocar.email="
})
@AutoConfigureMockMvc
class BootstrapRecursosTests {

    private static final String CSS = "/webjars/bootstrap/5.3.9/dist/css/bootstrap.min.css";
    private static final String JAVASCRIPT = "/webjars/bootstrap/5.3.9/dist/js/bootstrap.bundle.min.js";
    private static final String CSS_APP = "/css/app.css";
    private static final String JAVASCRIPT_APP = "/js/app.js";
    private static final String CSS_ANTIGUO = "/webjars/bootstrap/5.3.9/css/bootstrap.min.css";
    private static final String JAVASCRIPT_ANTIGUO = "/webjars/bootstrap/5.3.9/js/bootstrap.bundle.min.js";
    private static final Pattern CSRF_OCULTO = Pattern.compile(
            "<input\\b(?=[^>]*\\btype=\"hidden\")(?=[^>]*\\bname=\"_csrf\")"
                    + "(?=[^>]*\\bvalue=\"[^\"]+\")[^>]*>",
            Pattern.CASE_INSENSITIVE);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UsuarioRepository usuarioRepository;

    @MockitoBean
    private ContactoRepository contactoRepository;

    @MockitoBean
    private ConfirmacionEliminacionRepository confirmacionEliminacionRepository;

    @MockitoBean
    private SesionRevocacionJdbcRepository sesionRevocacionJdbcRepository;

    @MockitoBean
    private SesionRollbackJdbcRepository sesionRollbackJdbcRepository;

    @MockitoBean
    private ContactoService contactoService;

    @MockitoBean
    private EliminacionContactoService eliminacionContactoService;

    @Test
    void cssBootstrapSeSirveSinAutenticacion() throws Exception {
        mockMvc.perform(get(CSS))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("css")))
                .andExpect(content().string(containsString("bootstrap")))
                .andExpect(content().string(not(containsString("<html"))));
    }

    @Test
    void javascriptBootstrapSeSirveSinAutenticacion() throws Exception {
        mockMvc.perform(get(JAVASCRIPT))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("javascript")))
                .andExpect(content().string(containsString("bootstrap")))
                .andExpect(content().string(not(containsString("<html"))));
    }

    @Test
    void cssAplicacionSeSirveSinAutenticacion() throws Exception {
        mockMvc.perform(get(CSS_APP))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("css")))
                .andExpect(content().string(containsString("app-sidebar")))
                .andExpect(content().string(not(containsString("<html"))));
    }

    @Test
    void javascriptAplicacionSeSirveSinAutenticacion() throws Exception {
        mockMvc.perform(get(JAVASCRIPT_APP))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("javascript")))
                .andExpect(content().string(containsString("formulario-edicion")))
                .andExpect(content().string(not(containsString("<html"))));
    }

    @Test
    void layoutsUsanRutasBootstrapCorregidas() throws Exception {
        String[] layouts = {
                "templates/layout/base.html",
                "templates/layout/auth.html"
        };

        for (String layout : layouts) {
            String contenido = new ClassPathResource(layout).getContentAsString(StandardCharsets.UTF_8);
            assertTrue(contenido.contains(CSS), layout + " no referencia el CSS corregido");
            assertTrue(contenido.contains(JAVASCRIPT), layout + " no referencia el JavaScript corregido");
            assertTrue(contenido.contains(CSS_APP), layout + " no referencia el CSS de la aplicación");
            assertTrue(contenido.contains(JAVASCRIPT_APP), layout + " no referencia el JavaScript de la aplicación");
            assertFalse(contenido.contains(CSS_ANTIGUO), layout + " todavía referencia el CSS anterior");
            assertFalse(contenido.contains(JAVASCRIPT_ANTIGUO), layout + " todavía referencia el JavaScript anterior");
        }
    }

    @Test
    void vistasUsanLayoutsCompartidos() throws Exception {
        String[][] vistas = {
                {"templates/login.html", "layout/auth :: layout"},
                {"templates/index.html", "layout/base :: layout"},
                {"templates/contactos/listado.html", "layout/base :: layout"},
                {"templates/contactos/nuevo.html", "layout/base :: layout"},
                {"templates/contactos/editar.html", "layout/base :: layout"},
                {"templates/contactos/detalle.html", "layout/base :: layout"},
                {"templates/contactos/eliminar.html", "layout/base :: layout"},
                {"templates/contactos/no-encontrado.html", "layout/base :: layout"}
        };

        for (String[] vista : vistas) {
            String contenido = new ClassPathResource(vista[0]).getContentAsString(StandardCharsets.UTF_8);
            assertTrue(contenido.contains(vista[1]), vista[0] + " no usa el layout compartido");
            assertFalse(contenido.contains(CSS), vista[0] + " no debe referenciar Bootstrap directamente");
            assertFalse(contenido.contains(JAVASCRIPT), vista[0] + " no debe referenciar Bootstrap directamente");
            assertFalse(contenido.contains(CSS_ANTIGUO), vista[0] + " todavía referencia el CSS anterior");
            assertFalse(contenido.contains(JAVASCRIPT_ANTIGUO),
                    vista[0] + " todavía referencia el JavaScript anterior");
        }
    }

    @Test
    void loginRenderizaUnSoloMainYFormularioPostConCsrf() throws Exception {
        String html = obtenerHtml("/login");

        assertUnSoloMain(html);
        assertRecursosBootstrap(html);
        assertFormularioPostConCsrf(html, "/login");
        assertCampoConEtiqueta(html, "email", "username");
        assertCampoConEtiqueta(html, "password", "password");
    }

    @Test
    void editarRenderizaCamposRealesYFormularioPostConCsrf() throws Exception {
        when(contactoService.obtenerFormularioEdicion(7L)).thenReturn(formularioEdicion());

        String html = obtenerHtml("/contactos/{id}/editar", 7L);

        assertUnSoloMain(html);
        assertRecursosBootstrap(html);
        assertFormularioPostConCsrf(html, "/contactos/7/editar");
        assertCampoConEtiqueta(html, "nombres", "nombres");
        assertCampoConEtiqueta(html, "apellidos", "apellidos");
        assertCampoConEtiqueta(html, "dni", "dni");
        for (int indice = 0; indice < 2; indice++) {
            assertCampoConEtiqueta(html, "telefono" + indice, "telefonos[" + indice + "].numero");
            assertCampoConEtiqueta(html, "correo" + indice,
                    "correosElectronicos[" + indice + "].direccion");
        }
        assertFalse(html.contains("id=\"telefonos0\""));
        assertFalse(html.contains("id=\"correosElectronicos0\""));
    }

    @Test
    void eliminarRenderizaTokenDeConfirmacionYCsrfDentroDelFormularioPost() throws Exception {
        when(contactoService.obtenerDetalle(7L)).thenReturn(detalle());
        when(eliminacionContactoService.generar(eq(7L), anyString()))
                .thenReturn("token-confirmacion-prueba");

        String html = obtenerHtml("/contactos/{id}/eliminar", 7L);

        assertUnSoloMain(html);
        assertRecursosBootstrap(html);
        String formulario = extraerFormulario(html, "/contactos/7/eliminar");
        assertTrue(formulario.contains("name=\"token\""));
        assertTrue(formulario.contains("value=\"token-confirmacion-prueba\""));
        assertFormularioPostConCsrf(html, "/contactos/7/eliminar");
    }

    private String obtenerHtml(String ruta, Object... variables) throws Exception {
        return mockMvc.perform(get(ruta, variables).with(user("prueba").roles("USER")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private void assertUnSoloMain(String html) {
        assertEquals(1, contarOcurrencias(html, "<main\\b"));
        assertEquals(1, contarOcurrencias(html, "</main>"));
    }

    private void assertRecursosBootstrap(String html) {
        assertTrue(html.contains("/webjars/bootstrap/5.3.9/dist/css/bootstrap.min.css"));
        assertTrue(html.contains("/webjars/bootstrap/5.3.9/dist/js/bootstrap.bundle.min.js"));
        assertTrue(html.contains("/css/app.css"));
        assertTrue(html.contains("/js/app.js"));
    }

    private void assertFormularioPostConCsrf(String html, String accion) {
        String formulario = extraerFormulario(html, accion);
        Matcher metodo = Pattern.compile("\\bmethod=\"post\"", Pattern.CASE_INSENSITIVE)
                .matcher(formulario);
        assertTrue(metodo.find(), "El formulario " + accion + " no usa POST");
        assertTrue(CSRF_OCULTO.matcher(formulario).find(),
                "El formulario " + accion + " no renderiza el token CSRF");
    }

    private String extraerFormulario(String html, String accion) {
        Pattern patron = Pattern.compile(
                "<form\\b(?=[^>]*\\baction=\"" + Pattern.quote(accion) + "\")[^>]*>(.*?)</form>",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher coincidencia = patron.matcher(html);
        assertTrue(coincidencia.find(), "No se renderizó el formulario con acción " + accion);
        return coincidencia.group();
    }

    private void assertCampoConEtiqueta(String html, String id, String nombre) {
        Pattern entrada = Pattern.compile(
                "<input\\b(?=[^>]*\\bid=\"" + Pattern.quote(id) + "\")"
                        + "(?=[^>]*\\bname=\"" + Pattern.quote(nombre) + "\")[^>]*>",
                Pattern.CASE_INSENSITIVE);
        assertTrue(entrada.matcher(html).find(),
                "No se renderizó el campo " + nombre + " con id " + id);
        Pattern etiqueta = Pattern.compile(
                "<label\\b(?=[^>]*\\bfor=\"" + Pattern.quote(id) + "\")[^>]*>",
                Pattern.CASE_INSENSITIVE);
        assertTrue(etiqueta.matcher(html).find(),
                "No se renderizó la etiqueta asociada a " + id);
    }

    private int contarOcurrencias(String texto, String expresion) {
        Matcher coincidencias = Pattern.compile(expresion, Pattern.CASE_INSENSITIVE).matcher(texto);
        int total = 0;
        while (coincidencias.find()) {
            total++;
        }
        return total;
    }

    private ContactoEdicionForm formularioEdicion() {
        ContactoEdicionForm form = new ContactoEdicionForm();
        form.setNombres("Ana");
        form.setApellidos("Prueba");
        form.setDni("12345678");
        form.setTipoContacto(TipoContacto.CLIENTE);
        form.getTelefonos().add(new TelefonoForm("+51987654321"));
        form.getTelefonos().add(new TelefonoForm("+51987654322"));
        form.getCorreosElectronicos().add(new CorreoForm("ana@ejemplo.com"));
        form.getCorreosElectronicos().add(new CorreoForm("segunda@ejemplo.com"));
        return form;
    }

    private ContactoDetalleDto detalle() {
        return new ContactoDetalleDto(7L, "12345678", "CLIENTE", "Ana", "Prueba",
                List.of("+51987654321"), List.of("ana@ejemplo.com"), "image/png", 512);
    }

    @TestConfiguration
    static class ConfiguracionTransaccional {

        @Bean
        PlatformTransactionManager platformTransactionManager() {
            return new AbstractPlatformTransactionManager() {
                @Override
                protected Object doGetTransaction() {
                    return new Object();
                }

                @Override
                protected void doBegin(Object transaction, TransactionDefinition definition) {
                }

                @Override
                protected void doCommit(DefaultTransactionStatus status) {
                }

                @Override
                protected void doRollback(DefaultTransactionStatus status) {
                }
            };
        }
    }
}
