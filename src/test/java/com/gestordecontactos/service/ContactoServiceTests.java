package com.gestordecontactos.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gestordecontactos.dto.ContactoForm;
import com.gestordecontactos.dto.CorreoForm;
import com.gestordecontactos.dto.TelefonoForm;
import com.gestordecontactos.dto.TipoContacto;
import com.gestordecontactos.entity.Contacto;
import com.gestordecontactos.exception.DniDuplicadoException;
import com.gestordecontactos.repository.ContactoRepository;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;

class ContactoServiceTests {

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
    void crearNormalizaDatosYPersisteContacto() {
        when(contactoRepository.existsByDni("12345678")).thenReturn(false);
        when(fotoService.guardar(any())).thenReturn(new FotoService.FotoGuardada("uuid.png", "image/png", 1024));
        when(contactoRepository.save(any(Contacto.class))).thenAnswer(invocacion -> {
            Contacto guardado = invocacion.getArgument(0);
            guardado.setId(7L);
            return guardado;
        });

        assertEquals(7L, service.crear(formulario("987654321", " ANA@Ejemplo.COM ")));

        ArgumentCaptor<Contacto> captor = ArgumentCaptor.forClass(Contacto.class);
        verify(contactoRepository).save(captor.capture());
        Contacto guardado = captor.getValue();
        assertEquals("12345678", guardado.getDni());
        assertEquals("CLIENTE", guardado.getTipoContacto());
        assertEquals("Ana", guardado.getNombres());
        assertEquals("Pérez", guardado.getApellidos());
        assertEquals("uuid.png", guardado.getFotoArchivo());
        assertEquals("image/png", guardado.getFotoContentType());
        assertEquals(1024, guardado.getFotoTamanoBytes());
        assertEquals("+51987654321", guardado.getTelefonos().get(0).getNumero());
        assertEquals("ana@ejemplo.com", guardado.getCorreosElectronicos().get(0).getDireccion());
    }

    @Test
    void crearRechazaDniDuplicadoSinGuardarFoto() {
        when(contactoRepository.existsByDni("12345678")).thenReturn(true);

        assertThrows(DniDuplicadoException.class,
                () -> service.crear(formulario("987654321", "ana@ejemplo.com")));

        verify(fotoService, never()).guardar(any());
        verify(contactoRepository, never()).save(any());
    }

    @Test
    void crearTraduceDniDuplicadoPorRestriccionUnica() {
        when(contactoRepository.existsByDni("12345678")).thenReturn(false);
        when(fotoService.guardar(any())).thenReturn(new FotoService.FotoGuardada("uuid.png", "image/png", 1024));
        when(contactoRepository.save(any(Contacto.class)))
                .thenThrow(new DataIntegrityViolationException("save",
                        new SQLIntegrityConstraintViolationException(
                                "Duplicate entry '12345678' for key 'contactos.uq_contactos_dni'")));

        DniDuplicadoException ex = assertThrows(DniDuplicadoException.class,
                () -> service.crear(formulario("987654321", "ana@ejemplo.com")));

        assertEquals("Ya existe un contacto con el DNI 12345678", ex.getMessage());
    }

    @Test
    void obtenerDetalleDevuelveDatosDelContacto() {
        Contacto contacto = new Contacto();
        contacto.setId(9L);
        contacto.setDni("12345678");
        contacto.setTipoContacto("AMBOS");
        contacto.setNombres("Ana");
        contacto.setApellidos("Pérez");
        contacto.setFotoArchivo("uuid.png");
        contacto.setFotoContentType("image/png");
        contacto.setFotoTamanoBytes(512);
        contacto.addTelefono(telefono("+51987654321"));
        contacto.addCorreoElectronico(correo("ana@ejemplo.com"));
        when(contactoRepository.findById(9L)).thenReturn(Optional.of(contacto));

        var detalle = service.obtenerDetalle(9L);

        assertEquals("12345678", detalle.dni());
        assertEquals("AMBOS", detalle.tipoContacto());
        assertEquals(List.of("+51987654321"), detalle.telefonos());
        assertEquals(List.of("ana@ejemplo.com"), detalle.correosElectronicos());
    }

    private ContactoForm formulario(String telefono, String correo) {
        ContactoForm form = new ContactoForm();
        form.setNombres(" Ana ");
        form.setApellidos(" Pérez ");
        form.setDni(" 12345678 ");
        form.setTipoContacto(TipoContacto.CLIENTE);
        form.setTelefonos(List.of(new TelefonoForm(telefono)));
        form.setCorreosElectronicos(List.of(new CorreoForm(correo)));
        form.setFoto(new MockMultipartFile("foto", "foto.png", "image/png", new byte[] {1}));
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
