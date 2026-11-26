package com.gestordecontactos.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.gestordecontactos.dto.ContactoBusquedaForm;
import com.gestordecontactos.dto.ContactoListadoDto;
import com.gestordecontactos.entity.Contacto;
import com.gestordecontactos.repository.ContactoRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

class ContactoServiceListadoTests {

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
    void listarRecortaTerminoEscapaComodinesYNormalizaTipo() {
        when(contactoRepository.buscarContactos(eq(false), anyString(), eq("CLIENTE"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.listar(filtro("  a%b_c\\d  ", "cliente", 0, 10));

        ArgumentCaptor<String> captorPatron = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Pageable> captorPagina = ArgumentCaptor.forClass(Pageable.class);
        verify(contactoRepository).buscarContactos(eq(false), captorPatron.capture(), eq("CLIENTE"),
                captorPagina.capture());
        assertEquals("%a\\%b\\_c\\\\d%", captorPatron.getValue());

        Pageable pagina = captorPagina.getValue();
        assertEquals(0, pagina.getPageNumber());
        assertEquals(10, pagina.getPageSize());
        assertEquals(List.of("apellidos", "nombres", "id"),
                pagina.getSort().stream().map(Sort.Order::getProperty).toList());
        assertTrue(pagina.getSort().stream().allMatch(orden -> orden.getDirection() == Sort.Direction.ASC));
    }

    @Test
    void listarSinFiltrosNormalizaPaginaNegativaYTamanoNoPermitido() {
        when(contactoRepository.buscarContactos(eq(true), anyString(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.listar(filtro("   ", "", -5, 999));

        ArgumentCaptor<String> captorPatron = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Pageable> captorPagina = ArgumentCaptor.forClass(Pageable.class);
        verify(contactoRepository).buscarContactos(eq(true), captorPatron.capture(), isNull(),
                captorPagina.capture());
        assertEquals("%", captorPatron.getValue());
        assertEquals(0, captorPagina.getValue().getPageNumber());
        assertEquals(10, captorPagina.getValue().getPageSize());
    }

    @Test
    void listarAceptaTamanosPermitidos() {
        when(contactoRepository.buscarContactos(eq(true), anyString(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.listar(filtro("", "", 0, 20));
        service.listar(filtro("", "", 0, 50));

        ArgumentCaptor<Pageable> captorPagina = ArgumentCaptor.forClass(Pageable.class);
        verify(contactoRepository, times(2)).buscarContactos(eq(true), anyString(), isNull(),
                captorPagina.capture());
        assertEquals(20, captorPagina.getAllValues().get(0).getPageSize());
        assertEquals(50, captorPagina.getAllValues().get(1).getPageSize());
    }

    @Test
    void listarNormalizaParametrosNoNumericos() {
        when(contactoRepository.buscarContactos(eq(true), anyString(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        ContactoBusquedaForm filtro = new ContactoBusquedaForm();
        filtro.setPage("abc");
        filtro.setSize("abc");

        service.listar(filtro);

        ArgumentCaptor<Pageable> captorPagina = ArgumentCaptor.forClass(Pageable.class);
        verify(contactoRepository).buscarContactos(eq(true), anyString(), isNull(), captorPagina.capture());
        assertEquals(0, captorPagina.getValue().getPageNumber());
        assertEquals(10, captorPagina.getValue().getPageSize());
    }

    @Test
    void listarConvierteAContactoListadoDtoSinAccederAColeccionesLazy() {
        Contacto contacto = mock(Contacto.class);
        when(contacto.getId()).thenReturn(7L);
        when(contacto.getNombres()).thenReturn("Ana");
        when(contacto.getApellidos()).thenReturn("Perez");
        when(contacto.getDni()).thenReturn("12345678");
        when(contacto.getTipoContacto()).thenReturn("AMBOS");
        when(contactoRepository.buscarContactos(eq(true), anyString(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(contacto)));

        Page<ContactoListadoDto> resultado = service.listar(filtro("", "", 0, 10));

        assertEquals(1, resultado.getTotalElements());
        ContactoListadoDto dto = resultado.getContent().get(0);
        assertEquals(7L, dto.id());
        assertEquals("Ana", dto.nombres());
        assertEquals("Perez", dto.apellidos());
        assertEquals("12345678", dto.dni());
        assertEquals("AMBOS", dto.tipoContacto());
        verify(contacto, never()).getTelefonos();
        verify(contacto, never()).getCorreosElectronicos();
    }

    @Test
    void listarRechazaTerminoDemasiadoLargo() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.listar(filtro("a".repeat(101), "", 0, 10)));

        assertEquals("El término de búsqueda no puede superar 100 caracteres", ex.getMessage());
        verifyNoInteractions(contactoRepository);
    }

    @Test
    void listarRechazaClasificacionInvalida() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.listar(filtro("", "INVALIDO", 0, 10)));

        assertEquals("La clasificación indicada no es válida", ex.getMessage());
        verifyNoInteractions(contactoRepository);
    }

    private ContactoBusquedaForm filtro(String termino, String tipo, int page, int size) {
        ContactoBusquedaForm filtro = new ContactoBusquedaForm();
        filtro.setTermino(termino);
        filtro.setTipo(tipo);
        filtro.setPage(String.valueOf(page));
        filtro.setSize(String.valueOf(size));
        return filtro;
    }
}
