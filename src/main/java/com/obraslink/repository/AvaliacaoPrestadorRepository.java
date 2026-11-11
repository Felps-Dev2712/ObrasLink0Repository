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
