package com.gestordecontactos.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gestordecontactos.dto.ContactoEdicionForm;
import com.gestordecontactos.dto.CorreoForm;
import com.gestordecontactos.dto.TelefonoForm;
import com.gestordecontactos.dto.TipoContacto;
import com.gestordecontactos.entity.Contacto;
import com.gestordecontactos.exception.DniDuplicadoException;
import com.gestordecontactos.exception.RecursoNoEncontradoException;
import com.gestordecontactos.repository.ContactoRepository;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;

class ContactoServiceEdicionTests {

    private ContactoRepository contactoRepository;
    private FotoService fotoService;
    private ContactoService service;

    @BeforeEach
    void inicializar() {
        contactoRepository = mock(ContactoRepository.class);
        fotoService = mock(FotoService.class);
        service = new ContactoService(contactoRepository, fotoService);
    }

    @Test
    void obtenerFormularioEdicionDevuelveDatosActuales() {
        when(contactoRepository.findById(7L)).thenReturn(Optional.of(contacto("anterior.png")));

        ContactoEdicionForm form = service.obtenerFormularioEdicion(7L);

        assertEquals("Ana", form.getNombres());
        assertEquals("Pérez", form.getApellidos());
        assertEquals("12345678", form.getDni());
        assertEquals(TipoContacto.AMBOS, form.getTipoContacto());
        assertEquals("+51987654321", form.getTelefonos().get(0).getNumero());
        assertEquals("ana@ejemplo.com", form.getCorreosElectronicos().get(0).getDireccion());
    }

    @Test
    void actualizarConservaFotoYDniDelMismoContacto() {
        Contacto contacto = contacto("anterior.png");
        when(contactoRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(contacto));

        service.actualizar(7L, formulario("12345678", "+51987654322", "NUEVO@Ejemplo.COM"));

        assertEquals("12345678", contacto.getDni());
        assertEquals("anterior.png", contacto.getFotoArchivo());
        assertEquals("image/png", contacto.getFotoContentType());
        assertEquals(512, contacto.getFotoTamanoBytes());
        assertEquals("Nuevo", contacto.getNombres());
        assertEquals("López", contacto.getApellidos());
        assertEquals(TipoContacto.PROVEEDOR.name(), contacto.getTipoContacto());
        assertEquals("+51987654322", contacto.getTelefonos().get(0).getNumero());
        assertEquals("nuevo@ejemplo.com", contacto.getCorreosElectronicos().get(0).getDireccion());
        verify(contactoRepository, never()).existsByDniAndIdNot(anyString(), anyLong());
        verify(fotoService, never()).guardar(any());
        verify(fotoService, never()).eliminarTrasConfirmacion(anyString());
    }

    @Test
    void actualizarReemplazaFotoYProgramaEliminacionDeLaAnterior() {
        Contacto contacto = contacto("anterior.png");
        when(contactoRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(contacto));
        when(fotoService.guardar(any())).thenReturn(new FotoService.FotoGuardada("nueva.jpg", "image/jpeg", 2048));
        when(contactoRepository.existsByFotoArchivo("anterior.png")).thenReturn(false);
        ContactoEdicionForm form = formulario("12345678", "987654321", "ana@ejemplo.com");
        form.setFoto(new MockMultipartFile("foto", "foto.jpg", "image/jpeg", new byte[] {1}));

        service.actualizar(7L, form);

        assertEquals("nueva.jpg", contacto.getFotoArchivo());
        assertEquals("image/jpeg", contacto.getFotoContentType());
        assertEquals(2048, contacto.getFotoTamanoBytes());
        verify(contactoRepository).existsByFotoArchivo("anterior.png");
        verify(fotoService).eliminarTrasConfirmacion("anterior.png");
    }

    @Test
    void actualizarRechazaDniDeOtroContactoSinTocarFoto() {
        Contacto contacto = contacto("anterior.png");
        when(contactoRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(contacto));
        when(contactoRepository.existsByDniAndIdNot("87654321", 7L)).thenReturn(true);
        ContactoEdicionForm form = formulario("87654321", "987654321", "ana@ejemplo.com");
        form.setFoto(new MockMultipartFile("foto", "foto.png", "image/png", new byte[] {1}));

        assertThrows(DniDuplicadoException.class, () -> service.actualizar(7L, form));

        assertEquals("12345678", contacto.getDni());
        assertEquals("anterior.png", contacto.getFotoArchivo());
        verify(fotoService, never()).guardar(any());
        verify(contactoRepository, never()).saveAndFlush(any(Contacto.class));
        verify(fotoService, never()).eliminarTrasConfirmacion(anyString());
    }

    @Test
    void actualizarFallaSinProgramarEliminacionSiLaRestriccionDeDniFalla() {
        Contacto contacto = contacto("anterior.png");
        when(contactoRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(contacto));
        when(fotoService.guardar(any())).thenReturn(new FotoService.FotoGuardada("nueva.png", "image/png", 512));
        when(contactoRepository.saveAndFlush(any(Contacto.class)))
                .thenThrow(new DataIntegrityViolationException("save",
                        new SQLIntegrityConstraintViolationException(
                                "Duplicate entry '87654321' for key 'contactos.uq_contactos_dni'")));
        ContactoEdicionForm form = formulario("87654321", "987654321", "ana@ejemplo.com");
        form.setFoto(new MockMultipartFile("foto", "foto.png", "image/png", new byte[] {1}));

        assertThrows(DniDuplicadoException.class, () -> service.actualizar(7L, form));

        verify(fotoService, never()).eliminarTrasConfirmacion(anyString());
        verify(contactoRepository, never()).existsByFotoArchivo(anyString());
    }

    @Test
    void actualizarContactoInexistenteLanzaRecursoNoEncontrado() {
        when(contactoRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        assertThrows(RecursoNoEncontradoException.class,
                () -> service.actualizar(99L, formulario("12345678", "987654321", "ana@ejemplo.com")));

        verify(fotoService, never()).guardar(any());
        verify(contactoRepository, never()).saveAndFlush(any(Contacto.class));
    }

    private Contacto contacto(String nombreArchivo) {
        Contacto contacto = new Contacto();
        contacto.setId(7L);
        contacto.setDni("12345678");
        contacto.setTipoContacto("AMBOS");
        contacto.setNombres("Ana");
        contacto.setApellidos("Pérez");
        contacto.setFotoArchivo(nombreArchivo);
        contacto.setFotoContentType("image/png");
        contacto.setFotoTamanoBytes(512);
        contacto.addTelefono(telefono("+51987654321"));
        contacto.addCorreoElectronico(correo("ana@ejemplo.com"));
        return contacto;
    }

    private ContactoEdicionForm formulario(String dni, String telefono, String correo) {
        ContactoEdicionForm form = new ContactoEdicionForm();
        form.setNombres(" Nuevo ");
        form.setApellidos(" López ");
        form.setDni(dni);
        form.setTipoContacto(TipoContacto.PROVEEDOR);
        form.setTelefonos(java.util.List.of(new TelefonoForm(telefono)));
        form.setCorreosElectronicos(java.util.List.of(new CorreoForm(correo)));
        return form;
    }

    private com.gestordecontactos.entity.Telefono telefono(String numero) {
        com.gestordecontactos.entity.Telefono telefono = new com.gestordecontactos.entity.Telefono();
        telefono.setNumero(numero);
        return telefono;
    }

    private com.gestordecontactos.entity.CorreoElectronico correo(String direccion) {
        com.gestordecontactos.entity.CorreoElectronico correo =
                new com.gestordecontactos.entity.CorreoElectronico();
        correo.setDireccion(direccion);
        return correo;
    }
}
