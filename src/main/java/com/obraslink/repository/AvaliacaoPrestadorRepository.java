package com.obraslink.repository;

import com.obraslink.model.AvaliacaoPrestador;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /** Avaliação que o cliente fez do profissional em um atendimento, com o cliente na mesma consulta. */
    @Query("select a from AvaliacaoPrestador a join fetch a.cliente left join fetch a.servico where a.atendimentoId = :atendimentoId")
    java.util.Optional<AvaliacaoPrestador> findDetalheByAtendimentoId(@Param("atendimentoId") Long atendimentoId);

    /** Quais destes atendimentos já têm avaliação do cliente sobre o profissional (uma consulta só). */
    @Query("select a.atendimentoId from AvaliacaoPrestador a where a.atendimentoId in :ids")
    List<Long> findAtendimentoIdsAvaliados(@Param("ids") java.util.Collection<Long> ids);

    /**
     * Avaliações de um serviço: as ligadas direto ao serviço e as feitas a partir de um atendimento daquele serviço
     * (o fluxo atual grava o vínculo no atendimento). Cliente vem na mesma consulta para a lista não fazer N+1.
     */
    @Query("select a from AvaliacaoPrestador a join fetch a.cliente left join a.servico s "
            + "where a.prestador.id = :prestadorId and (s.id = :servicoId "
            + "or a.atendimentoId in (select at.id from Atendimento at where at.servicoId = :servicoId)) "
            + "order by a.criadoEm desc")
    List<AvaliacaoPrestador> findDoServico(@Param("prestadorId") Long prestadorId, @Param("servicoId") Long servicoId);

    /** Total e média geral do prestador direto no banco: [quantidade, média de 1 a 5]. */
    @Query("select count(a.id), avg((a.qualidade + a.prazo + a.preco + a.organizacao + a.atendimento) / 5.0) "
            + "from AvaliacaoPrestador a where a.prestador.id = :prestadorId")
    List<Object[]> resumoDoPrestador(@Param("prestadorId") Long prestadorId);

    long countByPrestadorId(Long prestadorId);

    boolean existsByClienteIdAndServicoId(Long clienteId, Long servicoId);

    boolean existsByAtendimentoId(Long atendimentoId);

    @Query("select p.id, p.nome, c.nome, count(a.id), avg((a.qualidade + a.prazo + a.preco + a.organizacao + a.atendimento) / 5.0) " +
            "from AvaliacaoPrestador a join a.prestador p left join p.categoria c where p.ativo = true " +
            "group by p.id, p.nome, c.nome having count(a.id) >= :minimo order by avg((a.qualidade + a.prazo + a.preco + a.organizacao + a.atendimento) / 5.0) desc, count(a.id) desc, p.id asc")
    List<Object[]> melhoresAvaliacoes(@Param("minimo") long minimo, Pageable pageable);

    @Query("select count(a.id), sum(case when a.qualidade >= 4 then 1 else 0 end), sum(case when a.prazo >= 4 then 1 else 0 end), sum(case when a.preco >= 4 then 1 else 0 end), sum(case when a.organizacao >= 4 then 1 else 0 end), sum(case when a.atendimento >= 4 then 1 else 0 end) from AvaliacaoPrestador a")
    List<Object[]> satisfacaoAgregada();
}
