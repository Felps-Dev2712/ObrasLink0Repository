package com.obraslink.repository;

import com.obraslink.model.SolicitacaoOrcamentoAnexo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SolicitacaoOrcamentoAnexoRepository extends JpaRepository<SolicitacaoOrcamentoAnexo, Long> {
    List<SolicitacaoOrcamentoAnexo> findBySolicitacaoIdOrderByIdAsc(Long solicitacaoId);
    Optional<SolicitacaoOrcamentoAnexo> findByIdAndSolicitacaoId(Long id, Long solicitacaoId);
    long countBySolicitacaoId(Long solicitacaoId);
}
