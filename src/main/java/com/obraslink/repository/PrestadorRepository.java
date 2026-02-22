package com.obraslink.repository;

import com.obraslink.model.Prestador;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PrestadorRepository extends JpaRepository<Prestador, Long> {
    
    List<Prestador> findByAtivoTrue();
    
    List<Prestador> findByCategoriaIdAndAtivoTrue(Long categoriaId);
    
    @Query("SELECT p FROM Prestador p WHERE LOWER(p.nome) LIKE LOWER(CONCAT('%', :termo, '%')) AND p.ativo = true")
    List<Prestador> searchByNome(String termo);
}
