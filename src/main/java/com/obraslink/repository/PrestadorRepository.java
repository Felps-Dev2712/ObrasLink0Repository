package com.obraslink.repository;

import com.obraslink.model.Prestador;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

@Repository
public interface PrestadorRepository extends JpaRepository<Prestador, Long> {
    long countByAtivoTrue();
    @Query("select p from Prestador p left join fetch p.categoria where p.ativo = true and exists (select u.id from Usuario u where u.id = p.usuarioId and u.ativo = true) order by p.criadoEm desc, p.id desc")
    List<Prestador> findDestaques(org.springframework.data.domain.Pageable pageable);
    @Query(value = "SELECT count(*) > 0 FROM prestadores WHERE regexp_replace(upper(cpf_cnpj), '[.[:space:]/-]', '', 'g') = :documento AND id <> coalesce(:id, -1)", nativeQuery = true)
    boolean documentoEmUso(@Param("documento") String documento, @Param("id") Long id);
    
    List<Prestador> findByAtivoTrue();
    
    @Query("select distinct p from Prestador p left join p.categorias categoria where (p.categoria.id = :categoriaId or categoria.id = :categoriaId) and p.ativo = true and exists (select u.id from Usuario u where u.id = p.usuarioId and u.ativo = true)")
    List<Prestador> findByCategoriaIdAndAtivoTrue(@Param("categoriaId") Long categoriaId);

    Optional<Prestador> findByUsuarioId(Long usuarioId);
    @Query("select p from Prestador p left join fetch p.categoria where p.id=:id and p.ativo=true and exists (select u.id from Usuario u where u.id=p.usuarioId and u.ativo=true)")
    Optional<Prestador> findPerfilPublico(@Param("id") Long id);

    boolean existsByIdAndUsuarioId(Long id, Long usuarioId);

    boolean existsByEmail(String email);

    boolean existsByCpfCnpj(String cpfCnpj);
    
    @Query("SELECT p FROM Prestador p WHERE LOWER(p.nome) LIKE LOWER(CONCAT('%', :termo, '%')) AND p.ativo = true")
    List<Prestador> searchByNome(String termo);
}
