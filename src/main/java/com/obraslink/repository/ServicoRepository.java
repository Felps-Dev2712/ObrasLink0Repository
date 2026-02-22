package com.obraslink.repository;

import com.obraslink.model.Servico;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ServicoRepository extends JpaRepository<Servico, Long> {
    
    List<Servico> findByDisponivelTrue();
    
    List<Servico> findByCategoriaIdAndDisponivelTrue(Long categoriaId);
    
    List<Servico> findByPrestadorId(Long prestadorId);
}
