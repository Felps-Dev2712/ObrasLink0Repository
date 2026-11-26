package com.obraslink.repository;

import com.obraslink.model.AgendaDia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface AgendaDiaRepository extends JpaRepository<AgendaDia, Long> {
    List<AgendaDia> findByPrestadorIdAndDataBetweenOrderByDataAsc(Long prestadorId, LocalDate inicio, LocalDate fim);
    void deleteByPrestadorId(Long prestadorId);
    void deleteByPrestadorIdAndDataBetween(Long prestadorId, LocalDate inicio, LocalDate fim);
}
