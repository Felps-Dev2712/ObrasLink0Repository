package com.obraslink.service;

import com.obraslink.model.AceiteTermos;
import com.obraslink.model.Usuario;
import com.obraslink.repository.AceiteTermosRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TermosService {
    // Cada versão corresponde a documentos imutáveis na pasta templates/legal.
    public static final String VERSAO_ATUAL = "2026-09-09-v1";
    private final AcessoService acessoService;
    private final AceiteTermosRepository aceites;
    private final EntityManager entityManager;

    @Transactional(readOnly = true)
    public boolean aceitouVersaoAtual(Long usuarioId) {
        return aceites.existsByUsuarioIdAndVersao(usuarioId, VERSAO_ATUAL);
    }

    @Transactional
    public void aceitar(String versao, boolean aceitouTermos, boolean cientePrivacidade) {
        if (!VERSAO_ATUAL.equals(versao)) {
            throw new IllegalArgumentException("Os documentos foram atualizados. Leia a versão atual antes de continuar.");
        }
        if (!aceitouTermos || !cientePrivacidade) {
            throw new IllegalArgumentException("Para continuar, aceite os Termos de Uso e confirme a leitura do Aviso de Privacidade.");
        }
        Long usuarioId = acessoService.getUsuarioAtualOrThrow().getId();
        Usuario usuario = entityManager.find(Usuario.class, usuarioId, LockModeType.PESSIMISTIC_WRITE);
        if (usuario == null || !Boolean.TRUE.equals(usuario.getAtivo())) {
            throw new IllegalArgumentException("Sua conta não está disponível para registrar o aceite.");
        }
        if (!aceitouVersaoAtual(usuarioId)) {
            aceites.save(new AceiteTermos(usuarioId, VERSAO_ATUAL));
        }
    }
}
