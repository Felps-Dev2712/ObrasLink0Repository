package com.obraslink.service;

import com.obraslink.model.Prestador;
import com.obraslink.repository.PrestadorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PrestadorService {
    
    private final PrestadorRepository prestadorRepository;
    
    public List<Prestador> findAllActive() {
        return prestadorRepository.findByAtivoTrue();
    }
    
    public List<Prestador> findByCategoria(Long categoriaId) {
        return prestadorRepository.findByCategoriaIdAndAtivoTrue(categoriaId);
    }
    
    public List<Prestador> search(String termo) {
        if (termo == null || termo.trim().isEmpty()) {
            return findAllActive();
        }
        return prestadorRepository.searchByNome(termo);
    }
    
    public Optional<Prestador> findById(Long id) {
        return prestadorRepository.findById(id);
    }

    public Optional<Prestador> findByUsuarioId(Long usuarioId) {
        return prestadorRepository.findByUsuarioId(usuarioId);
    }
    
    @Transactional
    public Prestador save(Prestador prestador) {
        if (prestador.getTelefones() != null) {
            prestador.getTelefones().forEach(t -> t.setPrestador(prestador));
        }
        return prestadorRepository.save(prestador);
    }
    
    @Transactional
    public void delete(Long id) {
        prestadorRepository.findById(id).ifPresent(p -> {
            p.setAtivo(false);
            prestadorRepository.save(p);
        });
    }
}
