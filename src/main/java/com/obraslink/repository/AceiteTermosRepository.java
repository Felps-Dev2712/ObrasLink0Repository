package com.obraslink.repository;

import com.obraslink.model.AceiteTermos;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AceiteTermosRepository extends JpaRepository<AceiteTermos, Long> {
    boolean existsByUsuarioIdAndVersao(Long usuarioId, String versao);
    void deleteByUsuarioId(Long usuarioId);
}
