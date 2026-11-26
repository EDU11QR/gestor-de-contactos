package com.gestordecontactos.exception;

import com.gestordecontactos.dto.ContactoForm;
import com.gestordecontactos.dto.CorreoForm;
import com.gestordecontactos.dto.TelefonoForm;
import com.gestordecontactos.dto.TipoContacto;
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

    private static final String MENSAJE_TAMANO_FOTO = "La fotografía no puede superar 2 MB";

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<String> integridadViolada(DataIntegrityViolationException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body("Los datos del contacto violan una restricción de integridad.");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ModelAndView tamanoFotoExcedido() {
        return formularioConErrorFoto();
    }

    @ExceptionHandler(MultipartException.class)
    public ModelAndView multipartInvalido(MultipartException ex) {
        if (!contieneExcesoTamano(ex)) {
            throw ex;
        }
        return formularioConErrorFoto();
    }

    private ModelAndView formularioConErrorFoto() {
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
