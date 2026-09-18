package com.obraslink.repository;

import com.obraslink.model.AvaliacaoCliente;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface AvaliacaoClienteRepository extends JpaRepository<AvaliacaoCliente, Long> {
    Optional<AvaliacaoCliente> findByAtendimentoId(Long atendimentoId);
    void deleteByClienteUsuarioIdOrPrestadorUsuarioId(Long clienteUsuarioId, Long prestadorUsuarioId);
}
