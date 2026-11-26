package com.gestordecontactos.service;

import com.gestordecontactos.repository.ContactoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FotoReferenciaService {

    private final ContactoRepository contactoRepository;

    public FotoReferenciaService(ContactoRepository contactoRepository) {
        this.contactoRepository = contactoRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public boolean estaReferenciada(String nombreArchivo) {
        return contactoRepository.existsByFotoArchivo(nombreArchivo);
    }
}
