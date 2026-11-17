package com.obraslink.repository;

import com.obraslink.model.SolicitacaoOrcamentoItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SolicitacaoOrcamentoItemRepository extends JpaRepository<SolicitacaoOrcamentoItem, Long> {
    List<SolicitacaoOrcamentoItem> findBySolicitacaoIdOrderByIdAsc(Long solicitacaoId);
    Optional<SolicitacaoOrcamentoItem> findByIdAndSolicitacaoId(Long id, Long solicitacaoId);
}
