package com.obraslink.repository;

import com.obraslink.model.AvaliacaoPrestador;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AvaliacaoPrestadorRepository extends JpaRepository<AvaliacaoPrestador, Long> {

    @Modifying
    @Query("""
            delete from AvaliacaoPrestador a where a.cliente.id in
                (select c.id from Cliente c where c.usuarioId = :usuarioId)
            or a.prestador.id in
                (select p.id from Prestador p where p.usuarioId = :usuarioId)
            or a.servico.id in
                (select s.id from Servico s where s.prestador.usuarioId = :usuarioId)
            """)
    void deleteByConta(@Param("usuarioId") Long usuarioId);

    List<AvaliacaoPrestador> findByPrestadorIdOrderByCriadoEmDesc(Long prestadorId);

    long countByPrestadorId(Long prestadorId);

    boolean existsByClienteIdAndServicoId(Long clienteId, Long servicoId);

    boolean existsByAtendimentoId(Long atendimentoId);
}
