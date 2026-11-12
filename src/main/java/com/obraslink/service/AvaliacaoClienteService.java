package com.obraslink.service;

import com.obraslink.dto.AvaliacaoClienteForm;
import com.obraslink.model.*;
import com.obraslink.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AvaliacaoClienteService {
    @org.springframework.beans.factory.annotation.Autowired(required=false) private PerfilAtivoService perfilAtivo;
    private final AtendimentoRepository atendimentos;
    private final AvaliacaoClienteRepository avaliacoes;
    private final PrestadorRepository prestadores;
    private final AcessoService acesso;
    private final NotificacaoService notificacoes;

    public record Detalhes(Atendimento atendimento, AvaliacaoCliente avaliacao, boolean podeAvaliar) {}

    /** Atendimentos (entre os informados) em que o prestador já registrou a avaliação do cliente. */
    @Transactional(readOnly = true)
    public java.util.Set<Long> atendimentosComAvaliacaoRecebida(java.util.Collection<Long> atendimentoIds) {
        if (atendimentoIds == null || atendimentoIds.isEmpty()) return java.util.Set.of();
        return new java.util.HashSet<>(avaliacoes.findAtendimentoIdsAvaliados(atendimentoIds));
    }

    @Transactional(readOnly = true)
    public Detalhes consultar(Long id) {
        Usuario usuario = acesso.getUsuarioAtualOrThrow();
        Atendimento atendimento = atendimentos.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Atendimento não encontrado."));
        validarParticipante(atendimento, usuario);
        AvaliacaoCliente avaliacao = avaliacoes.findByAtendimentoId(id).orElse(null);
        return new Detalhes(atendimento, avaliacao, avaliacao == null && podeAvaliar(atendimento, usuario));
    }

    @Transactional
    public void avaliar(Long id, AvaliacaoClienteForm form) {
        Usuario usuario = acesso.getUsuarioAtualOrThrow();
        Atendimento atendimento = atendimentos.findByIdParaAvaliar(id)
                .orElseThrow(() -> new IllegalArgumentException("Atendimento não encontrado."));
        validarParticipante(atendimento, usuario);
        if (!podeAvaliar(atendimento, usuario)) {
            throw new IllegalArgumentException("Somente o prestador responsável pode avaliar o cliente após concluir o serviço.");
        }
        if (avaliacoes.findByAtendimentoId(id).isPresent()) {
            throw new IllegalArgumentException("Você já avaliou o cliente neste atendimento.");
        }
        validarNota(form.getComunicacao());
        validarNota(form.getPontualidade());
        validarNota(form.getCumprimentoCombinado());
        String comentario = com.obraslink.validation.Comentario.normalizar(form.getComentario());
        AvaliacaoCliente avaliacao = new AvaliacaoCliente();
        avaliacao.setAtendimentoId(id);
        avaliacao.setClienteUsuarioId(atendimento.getClienteUsuarioId());
        avaliacao.setPrestadorUsuarioId(usuario.getId());
        avaliacao.setComunicacao(form.getComunicacao());
        avaliacao.setPontualidade(form.getPontualidade());
        avaliacao.setCumprimentoCombinado(form.getCumprimentoCombinado());
        avaliacao.setComentario(comentario);
        avaliacoes.save(avaliacao);
        notificacoes.criar(atendimento.getClienteUsuarioId(),
                atendimento.getPrestadorNome() + " avaliou você pelo serviço "
                        + (atendimento.getServicoTitulo() == null ? "concluído" : atendimento.getServicoTitulo()) + ".",
                "/atendimentos/" + id + "/avaliacao-cliente");
    }

    private void validarParticipante(Atendimento atendimento, Usuario usuario) {
        if (!Boolean.TRUE.equals(usuario.getAtivo())
                || (!usuario.getId().equals(atendimento.getClienteUsuarioId())
                && !usuario.getId().equals(atendimento.getPrestadorUsuarioId()))) {
            throw new IllegalArgumentException("Você não tem acesso à avaliação deste atendimento.");
        }
        if(perfilAtivo!=null){var role=perfilAtivo.atual();if(usuario.getId().equals(atendimento.getClienteUsuarioId())&&role!=PapelUsuario.CLIENTE||usuario.getId().equals(atendimento.getPrestadorUsuarioId())&&role!=PapelUsuario.PRESTADOR)throw new IllegalArgumentException("Ative o perfil correspondente a este atendimento.");}
    }

    private boolean podeAvaliar(Atendimento atendimento, Usuario usuario) {
        return atendimento.getStatus() == Atendimento.Status.CONCLUIDO
                && usuario.getPapeis().contains(PapelUsuario.PRESTADOR)
                && usuario.getId().equals(atendimento.getPrestadorUsuarioId())
                && (perfilAtivo==null||perfilAtivo.atual()==PapelUsuario.PRESTADOR)
                && !usuario.getId().equals(atendimento.getClienteUsuarioId())
                && prestadores.findById(atendimento.getPrestadorId())
                    .map(p -> usuario.getId().equals(p.getUsuarioId())).orElse(false);
    }

    private void validarNota(Integer nota) {
        if (nota == null || nota < 1 || nota > 5) {
            throw new IllegalArgumentException("Selecione uma nota entre 1 e 5 para cada critério.");
        }
    }
}
