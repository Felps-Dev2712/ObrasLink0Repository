package com.obraslink.repository;

import com.obraslink.model.AvaliacaoCliente;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

public interface AvaliacaoClienteRepository extends JpaRepository<AvaliacaoCliente, Long> {
    Optional<AvaliacaoCliente> findByAtendimentoId(Long atendimentoId);
    List<AvaliacaoCliente> findByClienteUsuarioIdOrderByCriadoEmDesc(Long usuarioId);
    void deleteByClienteUsuarioIdOrPrestadorUsuarioId(Long clienteUsuarioId, Long prestadorUsuarioId);

    /** Quais destes atendimentos já têm avaliação do prestador sobre o cliente (uma consulta só, sem N+1). */
    @Query("select a.atendimentoId from AvaliacaoCliente a where a.atendimentoId in :ids")
    List<Long> findAtendimentoIdsAvaliados(@org.springframework.data.repository.query.Param("ids") java.util.Collection<Long> ids);

    @Query("select u.nome, a.comentario from AvaliacaoCliente a join Usuario u on u.id = a.clienteUsuarioId "
            + "where u.ativo = true and a.comentario is not null and trim(a.comentario) <> '' "
            + "and (a.comunicacao + a.pontualidade + a.cumprimentoCombinado) / 3.0 >= 4 "
            + "order by (a.comunicacao + a.pontualidade + a.cumprimentoCombinado) / 3.0 desc, a.criadoEm desc")
    List<Object[]> depoimentosPositivos(Pageable pageable);
}
