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
        return servicoRepository.findPublicosDisponiveis();
    }

    public List<Servico> findByDisponibilidade(Boolean disponivel) {
        return disponivel == null ? servicoRepository.findAll() : disponivel ? servicoRepository.findByDisponivelTrue() : servicoRepository.findByDisponivelFalse();
    }

    public List<Servico> findAll() {
        return servicoRepository.findAll();
    }

    public List<Servico> findByCategoria(Long categoriaId) {
        if (categoriaId == null) {
            return findAllAvailable();
        }
        return servicoRepository.findPublicosDisponiveisPorCategoria(categoriaId);
    }
    
    public List<Servico> findByPrestador(Long prestadorId) {
        return servicoRepository.findPublicosPorPrestador(prestadorId);
    }
    public List<Servico> findByPrestadorDisponibilidade(Long prestadorId,boolean disponivel){return disponivel?servicoRepository.findContrataveisPorPrestador(prestadorId):servicoRepository.findByPrestadorIdAndDisponivel(prestadorId,false);}

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
