package com.gestordecontactos.controller;

import com.gestordecontactos.dto.ContactoBusquedaForm;
import com.gestordecontactos.dto.ContactoDetalleDto;
import com.gestordecontactos.dto.ContactoEdicionForm;
import com.gestordecontactos.dto.ContactoForm;
import com.gestordecontactos.dto.CorreoForm;
import com.gestordecontactos.dto.TelefonoForm;
import com.gestordecontactos.dto.TipoContacto;
import com.gestordecontactos.exception.DniDuplicadoException;
import com.gestordecontactos.exception.FotoInvalidaException;
import com.gestordecontactos.service.ContactoService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/contactos")
public class ContactoController {

    private final ContactoService contactoService;

    public ContactoController(ContactoService contactoService) {
        this.contactoService = contactoService;
    }

    @GetMapping
    public String listar(@ModelAttribute("filtro") ContactoBusquedaForm filtro, Model model) {
        try {
            model.addAttribute("pagina", contactoService.listar(filtro));
        } catch (IllegalArgumentException ex) {
            model.addAttribute("errorFiltro", ex.getMessage());
            model.addAttribute("pagina", contactoService.listar(new ContactoBusquedaForm()));
        }
        model.addAttribute("tipos", TipoContacto.values());
        return "contactos/listado";
    }

    @GetMapping("/nuevo")
    public String nuevo(Model model) {
        prepararFormulario(model, new ContactoForm());
        return "contactos/nuevo";
    }

    @PostMapping
    public String crear(@Valid @ModelAttribute("form") ContactoForm form,
            BindingResult resultado, Model model, RedirectAttributes redirectAttributes) {
        if (resultado.hasErrors()) {
            prepararFormulario(model, form);
            return "contactos/nuevo";
        }

        try {
            Long id = contactoService.crear(form);
            redirectAttributes.addFlashAttribute("registrado", true);
            return "redirect:/contactos/" + id;
        } catch (DniDuplicadoException ex) {
            resultado.rejectValue("dni", "dni.duplicado", "Ya existe un contacto con ese DNI");
        } catch (FotoInvalidaException ex) {
            resultado.rejectValue("foto", "foto.invalida", ex.getMessage());
        }

        prepararFormulario(model, form);
        return "contactos/nuevo";
    }

    @GetMapping("/{id}/editar")
    public String editar(@PathVariable Long id, Model model) {
        prepararFormularioEdicion(model, id, contactoService.obtenerFormularioEdicion(id));
        return "contactos/editar";
    }

    @PostMapping("/{id}/editar")
    public String actualizar(@PathVariable Long id,
            @Valid @ModelAttribute("form") ContactoEdicionForm form,
            BindingResult resultado, Model model, RedirectAttributes redirectAttributes) {
        if (resultado.hasErrors()) {
            prepararFormularioEdicion(model, id, form);
            return "contactos/editar";
        }

        try {
            contactoService.actualizar(id, form);
            redirectAttributes.addFlashAttribute("actualizado", true);
            return "redirect:/contactos/" + id;
        } catch (DniDuplicadoException ex) {
            resultado.rejectValue("dni", "dni.duplicado", "Ya existe un contacto con ese DNI");
        } catch (FotoInvalidaException ex) {
            resultado.rejectValue("foto", "foto.invalida", ex.getMessage());
        }

        prepararFormularioEdicion(model, id, form);
        return "contactos/editar";
    }

    @GetMapping("/{id}")
    public String detalle(@PathVariable Long id, Model model) {
        ContactoDetalleDto contacto = contactoService.obtenerDetalle(id);
        model.addAttribute("contacto", contacto);
        return "contactos/detalle";
    }

    private void prepararFormulario(Model model, ContactoForm form) {
        if (form.getTelefonos().isEmpty()) {
            form.getTelefonos().add(new TelefonoForm(""));
        }
        if (form.getCorreosElectronicos().isEmpty()) {
            form.getCorreosElectronicos().add(new CorreoForm(""));
        }
        model.addAttribute("form", form);
        model.addAttribute("tipos", TipoContacto.values());
    }

    private void prepararFormularioEdicion(Model model, Long id, ContactoEdicionForm form) {
        model.addAttribute("id", id);
        if (form.getTelefonos().isEmpty()) {
            form.getTelefonos().add(new TelefonoForm(""));
        }
        if (form.getCorreosElectronicos().isEmpty()) {
            form.getCorreosElectronicos().add(new CorreoForm(""));
        }
        model.addAttribute("form", form);
        model.addAttribute("tipos", TipoContacto.values());
    }
}
