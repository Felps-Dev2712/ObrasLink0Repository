package com.obraslink.service;

import com.obraslink.model.Notificacao;
import com.obraslink.repository.NotificacaoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificacaoService {

    private final NotificacaoRepository notificacaoRepository;

    @Transactional
    public void criar(Long usuarioId, String mensagem, String link) {
        if (usuarioId == null) {
            return;
        }
        notificacaoRepository.save(Notificacao.builder()
                .usuarioId(usuarioId)
                .mensagem(mensagem)
                .link(link)
                .build());
    }

    public long contarNaoLidas(Long usuarioId) {
        return notificacaoRepository.countByUsuarioIdAndLidaFalse(usuarioId);
    }

    public List<Notificacao> ultimas(Long usuarioId) {
        return notificacaoRepository.findTop5ByUsuarioIdOrderByCriadoEmDesc(usuarioId);
    }

    @Transactional
    public List<Notificacao> listarEMarcarComoLidas(Long usuarioId) {
        List<Notificacao> notificacoes = notificacaoRepository.findByUsuarioIdOrderByCriadoEmDesc(usuarioId);
        notificacaoRepository.findByUsuarioIdAndLidaFalse(usuarioId)
                .forEach(notificacao -> notificacao.setLida(true));
        return notificacoes;
    }
}
