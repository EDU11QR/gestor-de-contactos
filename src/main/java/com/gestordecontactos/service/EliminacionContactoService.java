package com.gestordecontactos.service;

import com.gestordecontactos.exception.FalloConsumoConfirmacionException;
import com.gestordecontactos.exception.FalloEliminacionContactoException;
import com.gestordecontactos.exception.FalloPersistenciaConfirmacionException;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

@Service
public class EliminacionContactoService {

    private static final Logger LOGGER = LoggerFactory.getLogger(EliminacionContactoService.class);

    private final ConfirmacionEliminacionService confirmacionEliminacionService;
    private final ContactoService contactoService;

    public EliminacionContactoService(ConfirmacionEliminacionService confirmacionEliminacionService,
            ContactoService contactoService) {
        this.confirmacionEliminacionService =
                Objects.requireNonNull(confirmacionEliminacionService);
        this.contactoService = Objects.requireNonNull(contactoService);
    }

    public String generar(Long idContacto, String idSesion) {
        try {
            return confirmacionEliminacionService.generar(idContacto, idSesion);
        } catch (DataAccessException ex) {
            LOGGER.error("Fallo de persistencia al generar la confirmación de eliminación", ex);
            throw new FalloPersistenciaConfirmacionException(ex);
        }
    }

    public void eliminar(Long idContacto, String idSesion, String token) {
        try {
            confirmacionEliminacionService.consumir(idContacto, idSesion, token);
        } catch (DataAccessException ex) {
            LOGGER.error("Fallo al consumir la confirmación de eliminación", ex);
            throw new FalloConsumoConfirmacionException(ex);
        }

        try {
            contactoService.eliminar(idContacto);
        } catch (RuntimeException ex) {
            LOGGER.error("Fallo al eliminar el contacto tras una confirmación válida", ex);
            throw new FalloEliminacionContactoException(ex);
        }
    }
}
