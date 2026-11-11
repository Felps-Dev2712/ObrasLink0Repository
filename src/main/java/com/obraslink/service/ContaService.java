package com.obraslink.service;

import com.obraslink.model.Usuario;
import com.obraslink.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ContaService {
    private final AcessoService acessoService;
    private final UsuarioRepository usuarioRepository;
    private final ClienteRepository clienteRepository;
    private final PrestadorRepository prestadorRepository;
    private final AvaliacaoPrestadorRepository avaliacaoRepository;
    private final AvaliacaoClienteRepository avaliacaoClienteRepository;
    private final NotificacaoRepository notificacaoRepository;

    private final AtendimentoRepository atendimentoRepository;
    private final AceiteTermosRepository aceiteTermosRepository;
    private final FavoritoRepository favoritos;
    private final DisponibilidadeRepository disponibilidades;
    private final CertificacaoRepository certificacoes;

    @Transactional
    public void excluirMinhaConta() {
        Usuario usuario = acessoService.getUsuarioAtualOrThrow();
        // Remove as referencias antes dos perfis e dos servicos em cascata.
        aceiteTermosRepository.deleteByUsuarioId(usuario.getId());
        clienteRepository.findByUsuarioId(usuario.getId()).ifPresent(c->favoritos.deleteByClienteId(c.getId()));
        prestadorRepository.findByUsuarioId(usuario.getId()).ifPresent(p->{favoritos.deleteByPrestadorId(p.getId());disponibilidades.deleteByPrestadorId(p.getId());certificacoes.deleteByPrestadorId(p.getId());});
        avaliacaoClienteRepository.deleteByClienteUsuarioIdOrPrestadorUsuarioId(usuario.getId(), usuario.getId());
        atendimentoRepository.deleteByClienteUsuarioIdOrPrestadorUsuarioId(usuario.getId(), usuario.getId());
        avaliacaoRepository.deleteByConta(usuario.getId());
        notificacaoRepository.deleteByUsuarioId(usuario.getId());
        clienteRepository.findByUsuarioId(usuario.getId()).ifPresent(clienteRepository::delete);
        prestadorRepository.findByUsuarioId(usuario.getId()).ifPresent(prestadorRepository::delete);
        usuarioRepository.delete(usuario);
    }
}
