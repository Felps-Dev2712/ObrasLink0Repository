package com.obraslink.repository;
import com.obraslink.model.Disponibilidade;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface DisponibilidadeRepository extends JpaRepository<Disponibilidade,Long> {
 List<Disponibilidade> findByPrestadorIdOrderByDiaAscInicioAsc(Long prestadorId);
 void deleteByPrestadorId(Long prestadorId);
}
