package com.obraslink.repository;

import com.obraslink.model.Atendimento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface AtendimentoRepository extends JpaRepository<Atendimento, Long> {
    List<Atendimento> findByPrestadorUsuarioIdOrderByIdDesc(Long usuarioId);
    @Query("""
            select a from Atendimento a
            where a.clienteUsuarioId = :usuarioId and a.prestadorId = :prestadorId
            and a.status = :status and a.clienteUsuarioId <> a.prestadorUsuarioId
            and not exists (select v.id from AvaliacaoPrestador v where v.atendimentoId = a.id)
            order by a.concluidoEm desc, a.id desc
            """)
    List<Atendimento> findDisponiveisParaAvaliar(@Param("usuarioId") Long usuarioId,
            @Param("prestadorId") Long prestadorId, @Param("status") Atendimento.Status status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Atendimento a where a.id = :id")
    Optional<Atendimento> findByIdParaAvaliar(@Param("id") Long id);
    Optional<Atendimento> findFirstByClienteUsuarioIdAndPrestadorIdAndStatusNotOrderByIdDesc(
            Long clienteId, Long prestadorId, Atendimento.Status status);
    List<Atendimento> findByClienteUsuarioIdOrPrestadorUsuarioIdOrderByIdDesc(Long clienteId, Long fornecedorId);
    void deleteByClienteUsuarioIdOrPrestadorUsuarioId(Long clienteId, Long fornecedorId);
}
