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
    private final ServicoFotoRepository servicoFotos;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private ImagemService imagemService;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private CertificacaoService certificacaoService;

    @Transactional
    public void excluirMinhaConta() {
        Usuario usuario = acessoService.getUsuarioAtualOrThrow();
        // Comprovantes das certificações: arquivos apagados do disco depois do commit (as linhas saem abaixo).
        if (certificacaoService != null) {
            prestadorRepository.findByUsuarioId(usuario.getId())
                    .ifPresent(p -> certificacaoService.excluirDocumentosDoPrestadorAposConfirmar(p.getId()));
        }
        // Imagens: guarda as chaves para apagar os arquivos depois que a exclusão for confirmada.
        java.util.List<String> imagens = new java.util.ArrayList<>(servicoFotos.chavesDaConta(usuario.getId()));
        prestadorRepository.findByUsuarioId(usuario.getId()).ifPresent(p -> {
            if (p.getFotoPerfilChave() != null) imagens.add(p.getFotoPerfilChave());
            if (p.getBannerChave() != null) imagens.add(p.getBannerChave());
        });
        clienteRepository.findByUsuarioId(usuario.getId())
                .map(c -> c.getFotoPerfilChave()).ifPresent(imagens::add);
        servicoFotos.deleteDaConta(usuario.getId());
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
        if (imagemService != null) imagemService.excluirArquivosAposConfirmar(imagens);
    }
}
