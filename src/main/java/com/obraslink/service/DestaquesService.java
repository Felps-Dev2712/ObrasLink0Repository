package com.obraslink.service;

import com.obraslink.repository.PrestadorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DestaquesService {
    private final PrestadorRepository prestadores;
    public record Destaque(Long id, String nome, String especialidade, String inicial) {}
    @Transactional(readOnly = true)
    public List<Destaque> listar() {
        return prestadores.findDestaques(PageRequest.of(0, 6)).stream()
                .map(p -> new Destaque(p.getId(), p.getNome(), p.getCategoria() == null ? "Serviços de construção" : p.getCategoria().getNome(),
                        p.getNome() == null || p.getNome().isBlank() ? "P" : p.getNome().substring(0, 1).toUpperCase(java.util.Locale.ROOT)))
                .toList();
    }
}
