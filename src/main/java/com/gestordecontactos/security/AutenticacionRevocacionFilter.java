package com.gestordecontactos.security;

import com.gestordecontactos.repository.SesionRollbackJdbcRepository;
import com.gestordecontactos.repository.SesionRollbackJdbcRepository.SesionSnapshot;
import com.gestordecontactos.repository.UsuarioRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.web.filter.OncePerRequestFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AutenticacionRevocacionFilter extends OncePerRequestFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(AutenticacionRevocacionFilter.class);
    private static final String RUTA_LOGIN = "/login";
    private static final String PARAMETRO_USUARIO = "username";
    private static final String CABECERA_SET_COOKIE = "Set-Cookie";

    private final UsuarioRepository usuarioRepository;
    private final PlatformTransactionManager transactionManager;
    private final ObjectProvider<SessionRepository<? extends Session>> repositorioSesiones;
    private final SesionRollbackJdbcRepository repositorioRecuperacion;
    private final String nombreCookieSesion;
    private final DefaultTransactionDefinition definicionTransaccion = new DefaultTransactionDefinition();

    public AutenticacionRevocacionFilter(UsuarioRepository usuarioRepository,
            PlatformTransactionManager transactionManager,
            ObjectProvider<SessionRepository<? extends Session>> repositorioSesiones,
            SesionRollbackJdbcRepository repositorioRecuperacion,
            @Value("${server.servlet.session.cookie.name:GESTOR_SESSION}") String nombreCookieSesion) {
        this.usuarioRepository = usuarioRepository;
        this.transactionManager = transactionManager;
        this.repositorioSesiones = repositorioSesiones;
        this.repositorioRecuperacion = repositorioRecuperacion;
        this.nombreCookieSesion = nombreCookieSesion;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equalsIgnoreCase(request.getMethod()) || !RUTA_LOGIN.equals(resolverRuta(request));
    }

    private String resolverRuta(HttpServletRequest request) {
        String servletPath = request.getServletPath();
        if (servletPath != null && !servletPath.isBlank()) {
            return servletPath;
        }
        String uri = request.getRequestURI();
        String contexto = request.getContextPath();
        if (contexto != null && !contexto.isBlank() && uri.startsWith(contexto)) {
            return uri.substring(contexto.length());
        }
        return uri;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String email = UsuarioUserDetailsService.normalizar(request.getParameter(PARAMETRO_USUARIO));
        if (email.isBlank()) {
            chain.doFilter(request, response);
            return;
        }
        try {
            UsuarioUserDetailsService.validarLongitudPrincipal(email);
        } catch (IllegalArgumentException ex) {
            chain.doFilter(request, response);
            return;
        }

        String idSesionAnterior = leerIdSesion(request.getCookies());
        SesionSnapshot sesionAnterior = repositorioRecuperacion.buscar(idSesionAnterior);
        CapturadorCookieSesion respuesta = new CapturadorCookieSesion(response, nombreCookieSesion);
        TransactionStatus transaccion = transactionManager.getTransaction(definicionTransaccion);
        try {
            usuarioRepository.findByEmailForUpdate(email);
            chain.doFilter(request, respuesta);
            if (transaccion.isRollbackOnly()) {
                revertir(transaccion, idSesionAnterior, respuesta.idSesion, sesionAnterior, respuesta);
            } else {
                transactionManager.commit(transaccion);
                respuesta.publicar(true);
            }
        } catch (ServletException | IOException ex) {
            if (!transaccion.isCompleted()) {
                revertir(transaccion, idSesionAnterior, respuesta.idSesion, sesionAnterior, respuesta);
            }
            throw ex;
        } catch (RuntimeException ex) {
            if (!transaccion.isCompleted()) {
                revertir(transaccion, idSesionAnterior, respuesta.idSesion, sesionAnterior, respuesta);
            }
            throw ex;
        }
    }

    private void revertir(TransactionStatus transaccion, String idSesionAnterior,
            String idSesionCreada, SesionSnapshot sesionAnterior, CapturadorCookieSesion respuesta)
            throws IOException {
        try {
            if (!transaccion.isCompleted()) {
                transactionManager.rollback(transaccion);
            }
        } finally {
            try {
                restaurarSesion(idSesionCreada, idSesionAnterior, sesionAnterior);
            } finally {
                respuesta.publicar(false);
            }
        }
    }

    private void restaurarSesion(String idSesionCreada, String idSesionAnterior,
            SesionSnapshot sesionAnterior) {
        if (sesionAnterior != null) {
            try {
                String idActual = idSesionCreada != null ? idSesionCreada : idSesionAnterior;
                SesionSnapshot sesionActual = repositorioRecuperacion.buscar(idActual);
                if (sesionActual != null
                        && sesionActual.primaryId().equals(sesionAnterior.primaryId())) {
                    repositorioRecuperacion.restaurar(sesionAnterior);
                    return;
                }
            } catch (RuntimeException ex) {
                LOGGER.error("No se pudo restaurar la sesión previa durante un inicio de sesión fallido", ex);
                return;
            }
        }
        if (idSesionCreada == null || idSesionCreada.equals(idSesionAnterior)) {
            return;
        }
        SessionRepository<? extends Session> repositorio = repositorioSesiones.getIfAvailable();
        if (repositorio == null) {
            return;
        }
        try {
            repositorio.deleteById(idSesionCreada);
        } catch (RuntimeException ex) {
            LOGGER.error("No se pudo eliminar la sesión creada durante un inicio de sesión fallido", ex);
        }
    }

    private String leerIdSesion(Cookie[] cookies) {
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (nombreCookieSesion.equals(cookie.getName())) {
                return decodificarIdSesion(cookie.getValue());
            }
        }
        return null;
    }

    private String decodificarIdSesion(String valor) {
        try {
            return new String(Base64.getDecoder().decode(valor), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    static final class CapturadorCookieSesion extends HttpServletResponseWrapper {

        private final String nombreCookieSesion;
        private final List<CabeceraDiferida> cabeceras = new ArrayList<>();
        String idSesion;
        private String redireccion;
        private EstadoError error;
        private boolean vaciarSolicitado;

        CapturadorCookieSesion(HttpServletResponse response, String nombreCookieSesion) {
            super(response);
            this.nombreCookieSesion = nombreCookieSesion;
        }

        @Override
        public void addHeader(String nombre, String valor) {
            cabeceras.add(new CabeceraDiferida(nombre, valor, true, esCookieSesion(valor)));
            capturarIdSesion(nombre, valor);
        }

        @Override
        public void setHeader(String nombre, String valor) {
            cabeceras.add(new CabeceraDiferida(nombre, valor, false, esCookieSesion(valor)));
            capturarIdSesion(nombre, valor);
        }

        @Override
        public void sendRedirect(String ubicacion) throws IOException {
            redireccion = ubicacion;
        }

        @Override
        public void sendError(int codigo) {
            error = new EstadoError(codigo, null);
        }

        @Override
        public void sendError(int codigo, String mensaje) {
            error = new EstadoError(codigo, mensaje);
        }

        @Override
        public void flushBuffer() throws IOException {
            vaciarSolicitado = true;
        }

        @Override
        public String getHeader(String nombre) {
            if (CABECERA_SET_COOKIE.equalsIgnoreCase(nombre)) {
                for (int indice = cabeceras.size() - 1; indice >= 0; indice--) {
                    CabeceraDiferida cabecera = cabeceras.get(indice);
                    if (CABECERA_SET_COOKIE.equalsIgnoreCase(cabecera.nombre())) {
                        return cabecera.valor();
                    }
                }
            }
            return super.getHeader(nombre);
        }

        @Override
        public Collection<String> getHeaders(String nombre) {
            if (CABECERA_SET_COOKIE.equalsIgnoreCase(nombre)) {
                List<String> valores = cabeceras.stream()
                        .filter(cabecera -> CABECERA_SET_COOKIE.equalsIgnoreCase(cabecera.nombre()))
                        .map(CabeceraDiferida::valor)
                        .toList();
                if (!valores.isEmpty()) {
                    return valores;
                }
            }
            return super.getHeaders(nombre);
        }

        void publicar(boolean incluirCookieSesion) throws IOException {
            for (CabeceraDiferida cabecera : cabeceras) {
                if (cabecera.cookieSesion() && !incluirCookieSesion) {
                    continue;
                }
                if (cabecera.adicionar()) {
                    super.addHeader(cabecera.nombre(), cabecera.valor());
                } else {
                    super.setHeader(cabecera.nombre(), cabecera.valor());
                }
            }
            cabeceras.clear();
            if (redireccion != null) {
                super.sendRedirect(redireccion);
                return;
            }
            if (error != null) {
                if (error.mensaje() == null) {
                    super.sendError(error.codigo());
                } else {
                    super.sendError(error.codigo(), error.mensaje());
                }
                return;
            }
            if (vaciarSolicitado) {
                super.flushBuffer();
            }
        }

        private void capturarIdSesion(String nombre, String valor) {
            if (!CABECERA_SET_COOKIE.equalsIgnoreCase(nombre)) {
                return;
            }
            String id = leerIdDeSetCookie(valor);
            if (id != null && !id.isBlank()) {
                idSesion = id;
            }
        }

        private boolean esCookieSesion(String valor) {
            return valor != null && valor.startsWith(nombreCookieSesion + "=");
        }

        private String leerIdDeSetCookie(String cabecera) {
            String prefijo = nombreCookieSesion + "=";
            if (!cabecera.startsWith(prefijo)) {
                return null;
            }
            int fin = cabecera.indexOf(';', prefijo.length());
            String valor = fin >= 0 ? cabecera.substring(prefijo.length(), fin)
                    : cabecera.substring(prefijo.length());
            try {
                return new String(Base64.getDecoder().decode(valor), StandardCharsets.UTF_8);
            } catch (IllegalArgumentException ex) {
                return null;
            }
        }

        private record CabeceraDiferida(String nombre, String valor, boolean adicionar, boolean cookieSesion) {
        }

        private record EstadoError(int codigo, String mensaje) {
        }
    }
}
