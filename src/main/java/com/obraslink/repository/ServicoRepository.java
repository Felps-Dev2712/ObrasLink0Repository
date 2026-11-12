package com.obraslink.repository;

import com.obraslink.model.Servico;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Repository
public interface ServicoRepository extends JpaRepository<Servico, Long> {
    
    @Query("select s from Servico s where s.disponivel=true and s.prestador.ativo=true and exists (select u.id from Usuario u where u.id=s.prestador.usuarioId and u.ativo=true)")
    List<Servico> findPublicosDisponiveis();
    List<Servico> findByDisponivelFalse();
    List<Servico> findByDisponivelTrue();
    
    @Query("select s from Servico s where s.categoria.id=:categoriaId and s.disponivel=true and s.prestador.ativo=true and exists (select u.id from Usuario u where u.id=s.prestador.usuarioId and u.ativo=true)")
    List<Servico> findPublicosDisponiveisPorCategoria(@Param("categoriaId") Long categoriaId);
    
    List<Servico> findByPrestadorId(Long prestadorId);
    List<Servico> findByPrestadorIdAndDisponivel(Long prestadorId, Boolean disponivel);
    @Query("select s from Servico s where s.prestador.id=:prestadorId and s.disponivel=true and s.prestador.ativo=true and exists (select u.id from Usuario u where u.id=s.prestador.usuarioId and u.ativo=true)")
    List<Servico> findContrataveisPorPrestador(@Param("prestadorId") Long prestadorId);
    @Query("select s from Servico s where s.prestador.id=:prestadorId and s.prestador.ativo=true and exists (select u.id from Usuario u where u.id=s.prestador.usuarioId and u.ativo=true)")
    List<Servico> findPublicosPorPrestador(@Param("prestadorId") Long prestadorId);

    boolean existsByIdAndPrestadorUsuarioId(Long id, Long usuarioId);

    /** Serviço para a página pública de detalhes: prestador e conta ativos; traz prestador e categoria na mesma consulta. */
    @Query("select s from Servico s join fetch s.prestador p left join fetch s.categoria where s.id = :id and p.ativo = true "
            + "and exists (select u.id from Usuario u where u.id = p.usuarioId and u.ativo = true)")
    java.util.Optional<Servico> findPublicoPorId(@Param("id") Long id);
}
