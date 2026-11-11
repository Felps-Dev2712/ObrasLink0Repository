package com.obraslink.repository;

import com.obraslink.model.ServicoFoto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ServicoFotoRepository extends JpaRepository<ServicoFoto, Long> {
    List<ServicoFoto> findByServicoIdOrderByIdAsc(Long servicoId);

    /** Uma consulta para as fotos de vários serviços (evita N+1 nas listagens). */
    List<ServicoFoto> findByServicoIdInOrderByIdAsc(Collection<Long> servicoIds);

    long countByServicoId(Long servicoId);

    Optional<ServicoFoto> findByIdAndServicoId(Long id, Long servicoId);

    @Query("select f.chave from ServicoFoto f where f.servicoId in (select s.id from Servico s where s.prestador.usuarioId = :usuarioId)")
    List<String> chavesDaConta(@Param("usuarioId") Long usuarioId);

    @Modifying
    @Query("delete from ServicoFoto f where f.servicoId in (select s.id from Servico s where s.prestador.usuarioId = :usuarioId)")
    void deleteDaConta(@Param("usuarioId") Long usuarioId);
}
