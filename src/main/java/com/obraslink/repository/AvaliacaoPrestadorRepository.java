package com.obraslink.repository;

import com.obraslink.model.AvaliacaoPrestador;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AvaliacaoPrestadorRepository extends JpaRepository<AvaliacaoPrestador, Long> {

    List<AvaliacaoPrestador> findByPrestadorIdOrderByCriadoEmDesc(Long prestadorId);

    long countByPrestadorId(Long prestadorId);

    boolean existsByClienteIdAndServicoId(Long clienteId, Long servicoId);
}
