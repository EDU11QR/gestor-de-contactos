package com.gestordecontactos.service;

import com.gestordecontactos.dto.ContactoBusquedaForm;
import com.gestordecontactos.dto.ContactoDetalleDto;
import com.gestordecontactos.dto.ContactoEdicionForm;
import com.gestordecontactos.dto.ContactoForm;
import com.gestordecontactos.dto.ContactoListadoDto;
import com.gestordecontactos.dto.CorreoForm;
import com.gestordecontactos.dto.TelefonoForm;
import com.gestordecontactos.dto.TipoContacto;
import com.gestordecontactos.entity.Contacto;
import com.gestordecontactos.entity.CorreoElectronico;
import com.gestordecontactos.entity.Telefono;
import com.gestordecontactos.exception.DniDuplicadoException;
import com.gestordecontactos.exception.IntegridadExceptionTranslator;
import com.gestordecontactos.exception.RecursoNoEncontradoException;
import com.gestordecontactos.repository.ContactoRepository;
import jakarta.validation.ConstraintViolationException;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContactoService {

    private static final int MAX_LONGITUD_TERMINO = 100;
    private static final int TAMANO_PREDETERMINADO = 10;
    private static final Set<Integer> TAMANOS_PERMITIDOS = Set.of(10, 20, 50);

    private final ContactoRepository contactoRepository;
    private final FotoService fotoService;
    private final IntegridadExceptionTranslator traductor;

    public ContactoService(ContactoRepository contactoRepository, FotoService fotoService,
            IntegridadExceptionTranslator traductor) {
        this.contactoRepository = contactoRepository;
        this.fotoService = fotoService;
        this.traductor = traductor;
    }

    @Transactional
    public Long crear(ContactoForm form) {
        String dni = form.getDni().trim();
        if (contactoRepository.existsByDni(dni)) {
            throw new DniDuplicadoException(dni);
        }

        FotoService.FotoGuardada foto = fotoService.guardar(form.getFoto());

        Contacto contacto = new Contacto();
        contacto.setDni(dni);
        contacto.setTipoContacto(form.getTipoContacto().name());
        contacto.setNombres(form.getNombres().trim());
        contacto.setApellidos(form.getApellidos().trim());
        contacto.setFotoArchivo(foto.nombreArchivo());
        contacto.setFotoContentType(foto.contentType());
        contacto.setFotoTamanoBytes(foto.tamanoBytes());

        for (TelefonoForm telefonoForm : form.getTelefonos()) {
            Telefono telefono = new Telefono();
            telefono.setNumero(normalizarTelefono(telefonoForm.getNumero()));
            contacto.addTelefono(telefono);
        }

        for (CorreoForm correoForm : form.getCorreosElectronicos()) {
            CorreoElectronico correo = new CorreoElectronico();
            correo.setDireccion(correoForm.getDireccion().trim().toLowerCase(Locale.ROOT));
            contacto.addCorreoElectronico(correo);
        }

        return ejecutarConTraduccionIntegridad(() -> contactoRepository.save(contacto).getId(), dni);
    }

    @Transactional(readOnly = true)
    public ContactoDetalleDto obtenerDetalle(Long id) {
        Contacto contacto = contactoRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Contacto no encontrado"));

        return new ContactoDetalleDto(
                contacto.getId(),
                contacto.getDni(),
                contacto.getTipoContacto(),
                contacto.getNombres(),
                contacto.getApellidos(),
                contacto.getTelefonos().stream().map(Telefono::getNumero).toList(),
                contacto.getCorreosElectronicos().stream().map(CorreoElectronico::getDireccion).toList(),
                contacto.getFotoContentType(),
                contacto.getFotoTamanoBytes());
    }

    @Transactional(readOnly = true)
    public ContactoEdicionForm obtenerFormularioEdicion(Long id) {
        Contacto contacto = contactoRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Contacto no encontrado"));

        ContactoEdicionForm form = new ContactoEdicionForm();
        form.setNombres(contacto.getNombres());
        form.setApellidos(contacto.getApellidos());
        form.setDni(contacto.getDni());
        form.setTipoContacto(TipoContacto.valueOf(contacto.getTipoContacto()));
        for (Telefono telefono : contacto.getTelefonos()) {
            form.getTelefonos().add(new TelefonoForm(telefono.getNumero()));
        }
        for (CorreoElectronico correo : contacto.getCorreosElectronicos()) {
            form.getCorreosElectronicos().add(new CorreoForm(correo.getDireccion()));
        }
        return form;
    }

    @Transactional
    public void actualizar(Long id, ContactoEdicionForm form) {
        Contacto contacto = contactoRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Contacto no encontrado"));

        String dni = form.getDni().trim();
        if (!dni.equals(contacto.getDni()) && contactoRepository.existsByDniAndIdNot(dni, id)) {
            throw new DniDuplicadoException(dni);
        }

        String archivoAnterior = contacto.getFotoArchivo();
        FotoService.FotoGuardada foto = null;
        if (form.getFoto() != null && !form.getFoto().isEmpty()) {
            foto = fotoService.guardar(form.getFoto());
        }

        contacto.setDni(dni);
        contacto.setTipoContacto(form.getTipoContacto().name());
        contacto.setNombres(form.getNombres().trim());
        contacto.setApellidos(form.getApellidos().trim());
        if (foto != null) {
            contacto.setFotoArchivo(foto.nombreArchivo());
            contacto.setFotoContentType(foto.contentType());
            contacto.setFotoTamanoBytes(foto.tamanoBytes());
        }

        contacto.getTelefonos().clear();
        contacto.getCorreosElectronicos().clear();
        contactoRepository.flush();

        for (TelefonoForm telefonoForm : form.getTelefonos()) {
            Telefono telefono = new Telefono();
            telefono.setNumero(normalizarTelefono(telefonoForm.getNumero()));
            contacto.addTelefono(telefono);
        }
        for (CorreoForm correoForm : form.getCorreosElectronicos()) {
            CorreoElectronico correo = new CorreoElectronico();
            correo.setDireccion(correoForm.getDireccion().trim().toLowerCase(Locale.ROOT));
            contacto.addCorreoElectronico(correo);
        }

        ejecutarConTraduccionIntegridad(() -> {
            contactoRepository.saveAndFlush(contacto);
            return null;
        }, dni);

        if (foto != null && !archivoAnterior.equals(contacto.getFotoArchivo())
                && !contactoRepository.existsByFotoArchivo(archivoAnterior)) {
            fotoService.eliminarTrasConfirmacion(archivoAnterior);
        }
    }

    @Transactional
    public void eliminar(Long id) {
        Contacto contacto = contactoRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Contacto no encontrado"));

        String archivoAnterior = contacto.getFotoArchivo();
        contactoRepository.delete(contacto);
        if (archivoAnterior != null && !archivoAnterior.isBlank()) {
            fotoService.eliminarTrasConfirmacion(archivoAnterior);
        }
    }

    @Transactional(readOnly = true)
    public Page<ContactoListadoDto> listar(ContactoBusquedaForm filtro) {
        String termino = filtro.getTermino() == null ? "" : filtro.getTermino().trim();
        if (termino.length() > MAX_LONGITUD_TERMINO) {
            throw new IllegalArgumentException("El término de búsqueda no puede superar 100 caracteres");
        }

        boolean terminoVacio = termino.isEmpty();
        String patron = terminoVacio ? "%" : "%" + escaparComodines(termino) + "%";
        String tipo = normalizarTipo(filtro.getTipo());
        int page = normalizarPagina(filtro.getPage());
        int size = normalizarTamano(filtro.getSize());

        Pageable pagina = PageRequest.of(page, size,
                Sort.by("apellidos").ascending().and(Sort.by("nombres")).and(Sort.by("id")));

        return contactoRepository.buscarContactos(terminoVacio, patron, tipo, pagina)
                .map(this::aContactoListadoDto);
    }

    private int normalizarPagina(String page) {
        try {
            return Math.max(Integer.parseInt(page == null ? "" : page.trim()), 0);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private int normalizarTamano(String size) {
        try {
            int valor = Integer.parseInt(size == null ? "" : size.trim());
            return TAMANOS_PERMITIDOS.contains(valor) ? valor : TAMANO_PREDETERMINADO;
        } catch (NumberFormatException ex) {
            return TAMANO_PREDETERMINADO;
        }
    }

    private <T> T ejecutarConTraduccionIntegridad(Supplier<T> operacion, String dni) {
        try {
            return operacion.get();
        } catch (DataIntegrityViolationException ex) {
            throw traductor.traducirIntegridad(ex, dni);
        } catch (ConstraintViolationException ex) {
            throw traductor.traducirValidacion(ex);
        }
    }

    private String normalizarTelefono(String numero) {
        String resultado = numero.trim();
        if (resultado.startsWith("+51")) {
            resultado = resultado.substring(3);
        } else if (resultado.startsWith("51") && resultado.length() == 11) {
            resultado = resultado.substring(2);
        }
        return "+51" + resultado;
    }

    private String escaparComodines(String termino) {
        return termino.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    private String normalizarTipo(String tipo) {
        if (tipo == null || tipo.isBlank()) {
            return null;
        }
        try {
            return TipoContacto.valueOf(tipo.trim().toUpperCase(Locale.ROOT)).name();
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("La clasificación indicada no es válida");
        }
    }

    private ContactoListadoDto aContactoListadoDto(Contacto contacto) {
        return new ContactoListadoDto(
                contacto.getId(),
                contacto.getNombres(),
                contacto.getApellidos(),
                contacto.getDni(),
                contacto.getTipoContacto());
    }
}
