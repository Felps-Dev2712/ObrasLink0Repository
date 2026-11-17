package com.obraslink.repository;

import com.obraslink.model.SolicitacaoOrcamento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SolicitacaoOrcamentoRepository extends JpaRepository<SolicitacaoOrcamento, Long> {
    Optional<SolicitacaoOrcamento> findByIdAndClienteUsuarioId(Long id, Long clienteUsuarioId);
}
