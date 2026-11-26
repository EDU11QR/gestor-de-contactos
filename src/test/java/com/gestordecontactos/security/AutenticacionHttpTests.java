package com.gestordecontactos.security;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gestordecontactos.entity.Usuario;
import com.gestordecontactos.repository.ContactoRepository;
import com.gestordecontactos.repository.UsuarioRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.session.SessionAutoConfiguration"
})
@AutoConfigureMockMvc
class AutenticacionHttpTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UsuarioRepository usuarioRepository;

    @MockitoBean
    private ContactoRepository contactoRepository;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Test
    void inicioPublicoMuestraEnlaceDeLoginYOcultaLogout() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Iniciar sesión")))
                .andExpect(content().string(not(containsString("Cerrar sesión"))));
    }

    @Test
    void paginaLoginIncluyeCamposYTokenCsrf() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"username\"")))
                .andExpect(content().string(containsString("name=\"password\"")))
                .andExpect(content().string(containsString("_csrf")));
    }

    @Test
    void rutaProtegidaRedirigeAlLoginCuandoNoHayAutenticacion() throws Exception {
        mockMvc.perform(get("/contactos"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void loginValidoNormalizaCorreoYMuestraLogout() throws Exception {
        when(usuarioRepository.findByEmail("usuario@ejemplo.com"))
                .thenReturn(Optional.of(usuario(encoder.encode("Clave123!"))));

        MvcResult resultado = mockMvc.perform(formLogin()
                        .user(" USUARIO@Ejemplo.COM ")
                        .password("Clave123!"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andReturn();

        MockHttpSession sesion = (MockHttpSession) resultado.getRequest().getSession(false);
        mockMvc.perform(get("/").session(sesion))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Cerrar sesión")))
                .andExpect(content().string(not(containsString("Iniciar sesión"))));

        verify(usuarioRepository).findByEmail("usuario@ejemplo.com");
    }

    @Test
    void loginInvalidoRedirigeConError() throws Exception {
        when(usuarioRepository.findByEmail("usuario@ejemplo.com"))
                .thenReturn(Optional.of(usuario(encoder.encode("Clave123!"))));

        mockMvc.perform(formLogin()
                        .user("usuario@ejemplo.com")
                        .password("Incorrecta"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void vistaAutenticadaConPrincipalMuestraLogout() throws Exception {
        UsuarioPrincipal principal = UsuarioPrincipal.from(usuario(encoder.encode("Clave123!")));
        Authentication autenticacion = new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities());

        mockMvc.perform(get("/").with(authentication(autenticacion)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Cerrar sesión")))
                .andExpect(content().string(not(containsString("Iniciar sesión"))));
    }

    @Test
    void loginSinTokenCsrfEsRechazado() throws Exception {
        mockMvc.perform(post("/login")
                        .param("username", "usuario@ejemplo.com")
                        .param("password", "Clave123!"))
                .andExpect(status().isForbidden());
    }

    @Test
    void logoutSinTokenCsrfEsRechazado() throws Exception {
        when(usuarioRepository.findByEmail("usuario@ejemplo.com"))
                .thenReturn(Optional.of(usuario(encoder.encode("Clave123!"))));

        MvcResult resultado = mockMvc.perform(formLogin()
                        .user("usuario@ejemplo.com")
                        .password("Clave123!"))
                .andReturn();
        MockHttpSession sesion = (MockHttpSession) resultado.getRequest().getSession(false);

        mockMvc.perform(post("/logout").session(sesion))
                .andExpect(status().isForbidden());
    }

    @Test
    void logoutInvalidaLaSesionYBloqueaSuReutilizacion() throws Exception {
        when(usuarioRepository.findByEmail("usuario@ejemplo.com"))
                .thenReturn(Optional.of(usuario(encoder.encode("Clave123!"))));

        MvcResult resultado = mockMvc.perform(formLogin()
                        .user("usuario@ejemplo.com")
                        .password("Clave123!"))
                .andReturn();
        MockHttpSession sesionAnterior = (MockHttpSession) resultado.getRequest().getSession(false);

        mockMvc.perform(post("/logout").with(csrf()).session(sesionAnterior))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?logout"));

        assertTrue(sesionAnterior.isInvalid());
        MockHttpSession sesionReutilizada = new MockHttpSession(null, sesionAnterior.getId());
        mockMvc.perform(get("/contactos").session(sesionReutilizada))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    private Usuario usuario(String hash) {
        Usuario usuario = new Usuario();
        usuario.setId(1L);
        usuario.setEmail("usuario@ejemplo.com");
        usuario.setNombre("Usuario");
        usuario.setPasswordHash(hash);
        return usuario;
    }
}
