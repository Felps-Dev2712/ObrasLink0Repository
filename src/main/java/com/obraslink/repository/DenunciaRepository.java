package com.obraslink.repository;

import com.obraslink.model.Denuncia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface DenunciaRepository extends JpaRepository<Denuncia, Long> {
    Optional<Denuncia> findByAtendimentoIdAndAutorUsuarioId(Long atendimentoId, Long autorUsuarioId);

    List<Denuncia> findByEstadoOrderByCriadoEmAsc(Denuncia.Estado estado);

    @Query("select d.atendimentoId from Denuncia d where d.atendimentoId in :ids and d.autorUsuarioId = :autorUsuarioId")
    List<Long> findAtendimentoIdsDenunciados(@Param("ids") Collection<Long> ids, @Param("autorUsuarioId") Long autorUsuarioId);
}
