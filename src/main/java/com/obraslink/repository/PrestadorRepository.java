package com.obraslink.repository;

import com.obraslink.model.Prestador;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PrestadorRepository extends JpaRepository<Prestador, Long> {
    
    List<Prestador> findByAtivoTrue();
    
    List<Prestador> findByCategoriaIdAndAtivoTrue(Long categoriaId);

    Optional<Prestador> findByUsuarioId(Long usuarioId);

    boolean existsByIdAndUsuarioId(Long id, Long usuarioId);

    boolean existsByEmail(String email);

    boolean existsByCpfCnpj(String cpfCnpj);
    
    @Query("SELECT p FROM Prestador p WHERE LOWER(p.nome) LIKE LOWER(CONCAT('%', :termo, '%')) AND p.ativo = true")
    List<Prestador> searchByNome(String termo);
}
