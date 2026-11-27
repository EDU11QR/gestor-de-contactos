package com.gestordecontactos.exception;

import com.gestordecontactos.dto.ContactoEdicionForm;
import com.gestordecontactos.dto.ContactoForm;
import com.gestordecontactos.dto.CorreoForm;
import com.gestordecontactos.dto.TelefonoForm;
import com.gestordecontactos.dto.TipoContacto;
import com.gestordecontactos.service.ContactoService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.ModelAndView;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String MENSAJE_TAMANO_FOTO = "La fotografía no puede superar 2 MB";
    private static final String MENSAJE_CONFLICTO = "No se pudo completar la operación por un conflicto de datos.";
    private static final String MENSAJE_VALIDACION = "Los datos enviados no son válidos.";
    private static final Pattern RUTA_EDICION = Pattern.compile("^/contactos/(\\d+)/editar$");

    private final ContactoService contactoService;
    private final IntegridadExceptionTranslator traductor;

    public GlobalExceptionHandler(ContactoService contactoService, IntegridadExceptionTranslator traductor) {
        this.contactoService = contactoService;
        this.traductor = traductor;
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<String> integridadViolada(DataIntegrityViolationException ex) {
        RuntimeException traducido = traductor.traducirIntegridad(ex, null);
        LOGGER.warn("Violación de integridad no controlada: {}", traducido.getClass().getSimpleName());
        return respuestaConflicto();
    }

    @ExceptionHandler(ConflictoIntegridadException.class)
    public ResponseEntity<String> conflictoIntegridad(ConflictoIntegridadException ex) {
        LOGGER.warn("Conflicto de integridad controlado: {}", ex.getClass().getSimpleName());
        return respuestaConflicto();
    }

    @ExceptionHandler(ValidacionDominioException.class)
    public ResponseEntity<String> validacionDominio(ValidacionDominioException ex) {
        LOGGER.warn("Validación de dominio controlada: {}", ex.getClass().getSimpleName());
        return respuestaValidacion();
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<String> validacionJakarta(ConstraintViolationException ex) {
        ValidacionDominioException traducido = traductor.traducirValidacion(ex);
        LOGGER.warn("Validación de Jakarta no controlada: {}", traducido.getClass().getSimpleName());
        return respuestaValidacion();
    }

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ModelAndView recursoNoEncontrado() {
        ModelAndView modelo = new ModelAndView("contactos/no-encontrado");
        modelo.setStatus(HttpStatus.NOT_FOUND);
        return modelo;
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ModelAndView tamanoFotoExcedido(HttpServletRequest solicitud) {
        return formularioConErrorFoto(solicitud);
    }

    @ExceptionHandler(MultipartException.class)
    public ModelAndView multipartInvalido(MultipartException ex, HttpServletRequest solicitud) {
        if (!contieneExcesoTamano(ex)) {
            throw ex;
        }
        return formularioConErrorFoto(solicitud);
    }

    private ResponseEntity<String> respuestaConflicto() {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(MENSAJE_CONFLICTO);
    }

    private ResponseEntity<String> respuestaValidacion() {
        return ResponseEntity.badRequest().body(MENSAJE_VALIDACION);
    }

    private ModelAndView formularioConErrorFoto(HttpServletRequest solicitud) {
        Matcher coincidencia = RUTA_EDICION.matcher(solicitud.getServletPath());
        if (coincidencia.matches()) {
            return formularioEdicionConErrorFoto(Long.valueOf(coincidencia.group(1)));
        }
        return formularioAltaConErrorFoto();
    }

    private ModelAndView formularioAltaConErrorFoto() {
        ContactoForm form = new ContactoForm();
        form.getTelefonos().add(new TelefonoForm(""));
        form.getCorreosElectronicos().add(new CorreoForm(""));

        BindingResult resultado = new BeanPropertyBindingResult(form, "form");
        resultado.rejectValue("foto", "foto.demasiadoGrande", MENSAJE_TAMANO_FOTO);

        ModelAndView modelo = new ModelAndView("contactos/nuevo");
        modelo.addObject("form", form);
        modelo.addObject(BindingResult.MODEL_KEY_PREFIX + "form", resultado);
        modelo.addObject("tipos", TipoContacto.values());
        return modelo;
    }

    private ModelAndView formularioEdicionConErrorFoto(Long id) {
        ContactoEdicionForm form;
        try {
            form = contactoService.obtenerFormularioEdicion(id);
        } catch (RecursoNoEncontradoException ex) {
            return recursoNoEncontrado();
        }
        if (form.getTelefonos().isEmpty()) {
            form.getTelefonos().add(new TelefonoForm(""));
        }
        if (form.getCorreosElectronicos().isEmpty()) {
            form.getCorreosElectronicos().add(new CorreoForm(""));
        }

        BindingResult resultado = new BeanPropertyBindingResult(form, "form");
        resultado.rejectValue("foto", "foto.demasiadoGrande", MENSAJE_TAMANO_FOTO);

        ModelAndView modelo = new ModelAndView("contactos/editar");
        modelo.addObject("id", id);
        modelo.addObject("form", form);
        modelo.addObject(BindingResult.MODEL_KEY_PREFIX + "form", resultado);
        modelo.addObject("tipos", TipoContacto.values());
        return modelo;
    }

    private boolean contieneExcesoTamano(Throwable ex) {
        Throwable causa = ex;
        while (causa != null) {
            if (causa instanceof MaxUploadSizeExceededException) {
                return true;
            }
            causa = causa.getCause();
        }
        return false;
    }
}
