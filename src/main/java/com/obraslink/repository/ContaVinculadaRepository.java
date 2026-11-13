package com.obraslink.repository;

import com.obraslink.model.ContaVinculada;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface ContaVinculadaRepository extends JpaRepository<ContaVinculada, Long> {
    Optional<ContaVinculada> findByTokenHash(String tokenHash);

    void deleteByTokenHash(String tokenHash);

    void deleteByExpiraEmBefore(LocalDateTime limite);
}
