package com.obraslink.service;

import com.obraslink.model.Servico;
import com.obraslink.repository.ServicoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ServicoService {

    private final ServicoRepository servicoRepository;

    public List<Servico> findAllAvailable() {
        return servicoRepository.findByDisponivelTrue();
    }

    public List<Servico> findAll() {
        return servicoRepository.findAll();
    }

    public List<Servico> findByCategoria(Long categoriaId) {
        if (categoriaId == null) {
            return findAllAvailable();
        }
        return servicoRepository.findByCategoriaIdAndDisponivelTrue(categoriaId);
    }
    
    public List<Servico> findByPrestador(Long prestadorId) {
        return servicoRepository.findByPrestadorId(prestadorId);
    }

    public Optional<Servico> findById(Long id) {
        return servicoRepository.findById(id);
    }

    @Transactional
    public Servico save(Servico servico) {
        return servicoRepository.save(servico);
    }

    @Transactional
    public void delete(Long id) {
        servicoRepository.findById(id).ifPresent(s -> {
            s.setDisponivel(false);
            servicoRepository.save(s);
        });
    }
}
