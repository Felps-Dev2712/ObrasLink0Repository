package com.obraslink.repository;

import com.obraslink.model.Notificacao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificacaoRepository extends JpaRepository<Notificacao, Long> {
    void deleteByUsuarioId(Long usuarioId);
    List<Notificacao> findByUsuarioIdOrderByCriadoEmDesc(Long usuarioId);
    List<Notificacao> findTop5ByUsuarioIdOrderByCriadoEmDesc(Long usuarioId);
    long countByUsuarioIdAndLidaFalse(Long usuarioId);
    List<Notificacao> findByUsuarioIdAndLidaFalse(Long usuarioId);
}
