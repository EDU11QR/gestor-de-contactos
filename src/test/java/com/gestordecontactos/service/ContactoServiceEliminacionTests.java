package com.gestordecontactos.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gestordecontactos.entity.Contacto;
import com.gestordecontactos.exception.IntegridadExceptionTranslator;
import com.gestordecontactos.exception.RecursoNoEncontradoException;
import com.gestordecontactos.repository.ContactoRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class ContactoServiceEliminacionTests {

    private ContactoRepository contactoRepository;
    private FotoService fotoService;
    private ContactoService service;

    @BeforeEach
    void inicializar() {
        contactoRepository = mock(ContactoRepository.class);
        fotoService = mock(FotoService.class);
        service = new ContactoService(contactoRepository, fotoService, new IntegridadExceptionTranslator());
    }

    @Test
    void eliminaContactoExistenteConBloqueoYProgramaEliminacionDeFoto() {
        Contacto contacto = contacto("anterior.png");
        when(contactoRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(contacto));

        service.eliminar(7L);

        verify(contactoRepository).findByIdForUpdate(7L);
        verify(contactoRepository).delete(contacto);
        verify(fotoService).eliminarTrasConfirmacion("anterior.png");
    }

    @Test
    void capturaNombreDeFotoAntesDeEliminarContacto() {
        Contacto contacto = contacto("anterior.png");
        when(contactoRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(contacto));
        doAnswer(invocacion -> {
            contacto.setFotoArchivo("modificada.png");
            return null;
        }).when(contactoRepository).delete(contacto);

        service.eliminar(7L);

        verify(fotoService).eliminarTrasConfirmacion("anterior.png");
    }

    @Test
    void contactoInexistenteLanzaRecursoNoEncontradoYNoElimina() {
        when(contactoRepository.findByIdForUpdate(7L)).thenReturn(Optional.empty());

        assertThrows(RecursoNoEncontradoException.class, () -> service.eliminar(7L));

        verify(contactoRepository, never()).delete(any(Contacto.class));
        verify(fotoService, never()).eliminarTrasConfirmacion(any());
    }

    @Test
    void contactoSinFotografiaEliminaSinProgramarBorrado() {
        Contacto contacto = contacto(null);
        when(contactoRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(contacto));

        service.eliminar(7L);

        verify(contactoRepository).delete(contacto);
        verify(fotoService, never()).eliminarTrasConfirmacion(any());
    }

    @Test
    void noProgramaEliminacionDeFotoSiLaEliminacionFalla() {
        Contacto contacto = contacto("anterior.png");
        when(contactoRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(contacto));
        doThrow(new DataIntegrityViolationException("delete"))
                .when(contactoRepository).delete(contacto);

        assertThrows(DataIntegrityViolationException.class, () -> service.eliminar(7L));

        verify(fotoService, never()).eliminarTrasConfirmacion(any());
    }

    private Contacto contacto(String nombreArchivo) {
        Contacto contacto = new Contacto();
        contacto.setId(7L);
        contacto.setDni("12345678");
        contacto.setTipoContacto("CLIENTE");
        contacto.setNombres("Ana");
        contacto.setApellidos("Pérez");
        contacto.setFotoArchivo(nombreArchivo);
        contacto.setFotoContentType("image/png");
        contacto.setFotoTamanoBytes(512);
        return contacto;
    }
}
