package com.obraslink.service;

import com.obraslink.dto.AvaliacaoPrestadorForm;
import com.obraslink.model.AvaliacaoPrestador;
import com.obraslink.model.Cliente;
import com.obraslink.model.PapelUsuario;
import com.obraslink.model.Prestador;
import com.obraslink.model.Atendimento;
import com.obraslink.model.Usuario;
import com.obraslink.repository.AvaliacaoPrestadorRepository;
import com.obraslink.repository.ClienteRepository;
import com.obraslink.repository.PrestadorRepository;
import com.obraslink.repository.AtendimentoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AvaliacaoPrestadorService {
    @org.springframework.beans.factory.annotation.Autowired(required=false) private PerfilAtivoService perfilAtivo;

    private final AvaliacaoPrestadorRepository avaliacaoRepository;
    private final ClienteRepository clienteRepository;
    private final PrestadorRepository prestadorRepository;
    private final AtendimentoRepository atendimentoRepository;
    private final AcessoService acessoService;
    private final NotificacaoService notificacaoService;

    @Transactional(readOnly = true)
    public List<Atendimento> atendimentosDisponiveis(Long prestadorId) {
        return acessoService.getUsuarioAtual()
                .filter(usuario -> Boolean.TRUE.equals(usuario.getAtivo()) && usuario.getPapeis().contains(PapelUsuario.CLIENTE)&&(perfilAtivo==null||perfilAtivo.atual()==PapelUsuario.CLIENTE))
                .map(usuario -> atendimentoRepository.findDisponiveisParaAvaliar(usuario.getId(), prestadorId, Atendimento.Status.CONCLUIDO))
                .orElseGet(List::of);
    }

    /** Avaliação que o cliente fez do profissional em um atendimento (null se ainda não avaliou). */
    public record Recebida(Atendimento atendimento, AvaliacaoPrestador avaliacao, boolean souPrestador) {
        public java.math.BigDecimal media() {
            if (avaliacao == null) return null;
            double m = (avaliacao.getQualidade() + avaliacao.getPrazo() + avaliacao.getPreco() + avaliacao.getOrganizacao() + avaliacao.getAtendimento()) / 5.0;
            return java.math.BigDecimal.valueOf(m).setScale(1, java.math.RoundingMode.HALF_UP);
        }
    }

    /** Só os dois participantes do atendimento (o profissional avaliado e o cliente que avaliou) podem ver. */
    @Transactional(readOnly = true)
    public Recebida consultarRecebida(Long atendimentoId) {
        var usuario = acessoService.getUsuarioAtualOrThrow();
        Atendimento atendimento = atendimentoRepository.findById(atendimentoId)
                .orElseThrow(() -> new IllegalArgumentException("Atendimento não encontrado."));
        boolean prestador = usuario.getId().equals(atendimento.getPrestadorUsuarioId());
        if (!prestador && !usuario.getId().equals(atendimento.getClienteUsuarioId())) {
            throw new IllegalArgumentException("Você não tem acesso a esta avaliação.");
        }
        return new Recebida(atendimento, avaliacaoRepository.findDetalheByAtendimentoId(atendimentoId).orElse(null), prestador);
    }

    /** Atendimentos (entre os informados) em que o cliente já avaliou o profissional. */
    @Transactional(readOnly = true)
    public java.util.Set<Long> atendimentosComAvaliacaoDoCliente(java.util.Collection<Long> atendimentoIds) {
        if (atendimentoIds == null || atendimentoIds.isEmpty()) return java.util.Set.of();
        return new java.util.HashSet<>(avaliacaoRepository.findAtendimentoIdsAvaliados(atendimentoIds));
    }

    public List<AvaliacaoPrestador> findByPrestador(Long prestadorId) {
        return avaliacaoRepository.findByPrestadorIdOrderByCriadoEmDesc(prestadorId);
    }

    public long countByPrestador(Long prestadorId) {
        return avaliacaoRepository.countByPrestadorId(prestadorId);
    }

    public BigDecimal calcularMediaGeral(List<AvaliacaoPrestador> avaliacoes) {
        if (avaliacoes == null || avaliacoes.isEmpty()) {
            return BigDecimal.ZERO;
        }

        double media = avaliacoes.stream()
                .mapToDouble(this::calcularMedia)
                .average()
                .orElse(0);
        return BigDecimal.valueOf(media).setScale(1, RoundingMode.HALF_UP);
    }

    public double calcularMedia(AvaliacaoPrestador avaliacao) {
        return (avaliacao.getQualidade()
                + avaliacao.getPrazo()
                + avaliacao.getPreco()
                + avaliacao.getOrganizacao()
                + avaliacao.getAtendimento()) / 5.0;
    }

    @Transactional
    public void avaliar(Long prestadorId, Long atendimentoId, AvaliacaoPrestadorForm form) {
        Usuario usuario = acessoService.getUsuarioAtualOrThrow();
        if (!Boolean.TRUE.equals(usuario.getAtivo()) || !usuario.getPapeis().contains(PapelUsuario.CLIENTE)||(perfilAtivo!=null&&perfilAtivo.atual()!=PapelUsuario.CLIENTE)) {
            throw new IllegalArgumentException("Apenas clientes podem registrar avaliacoes.");
        }

        if (atendimentoId == null) throw new IllegalArgumentException("Atendimento concluído não informado.");
        Atendimento contratado = atendimentoRepository.findByIdParaAvaliar(atendimentoId)
                .orElseThrow(() -> new IllegalArgumentException("Serviço concluído não encontrado para avaliação."));
        if (!usuario.getId().equals(contratado.getClienteUsuarioId())
                || !prestadorId.equals(contratado.getPrestadorId())
                || contratado.getStatus() != Atendimento.Status.CONCLUIDO) {
            throw new IllegalArgumentException("Você só pode avaliar serviços que contratou e que este prestador já concluiu.");
        }

        Cliente cliente = clienteRepository.findByUsuarioId(usuario.getId())
                .orElseThrow(() -> new IllegalArgumentException("Seu usuario nao possui cadastro de cliente."));
        Prestador prestador = prestadorRepository.findById(prestadorId)
                .orElseThrow(() -> new IllegalArgumentException("Prestador nao encontrado."));
        if (prestador.getUsuarioId() != null && prestador.getUsuarioId().equals(usuario.getId())) {
            throw new IllegalArgumentException("Voce nao pode avaliar seu proprio perfil de prestador.");
        }
        if (!contratado.getPrestadorUsuarioId().equals(prestador.getUsuarioId())) {
            throw new IllegalArgumentException("Este atendimento não pertence à conta atual do prestador.");
        }
        if (avaliacaoRepository.existsByAtendimentoId(contratado.getId())) {
            throw new IllegalArgumentException("Você já avaliou este serviço concluído.");
        }

        validarNota(form.getQualidade(), "qualidade");
        validarNota(form.getPrazo(), "prazo");
        validarNota(form.getPreco(), "preco");
        validarNota(form.getOrganizacao(), "organizacao");
        validarNota(form.getAtendimento(), "atendimento");
        String comentario = com.obraslink.validation.Comentario.normalizar(form.getComentario());

        AvaliacaoPrestador avaliacao = AvaliacaoPrestador.builder()
                .prestador(prestador)
                .cliente(cliente)
                .atendimentoId(contratado.getId())
                .qualidade(form.getQualidade())
                .prazo(form.getPrazo())
                .preco(form.getPreco())
                .organizacao(form.getOrganizacao())
                .atendimento(form.getAtendimento())
                .comentario(comentario)
                .build();
        avaliacaoRepository.save(avaliacao);

        String link = "/prestadores/" + prestador.getId();
        notificacaoService.criar(usuario.getId(),
                "Sua avaliação para " + prestador.getNome() + " foi registrada.", link);
        notificacaoService.criar(prestador.getUsuarioId(),
                "Você recebeu uma nova avaliação de " + cliente.getNome() + ".", "/atendimentos/" + contratado.getId() + "/avaliacao-profissional");
    }

    private void validarNota(Integer nota, String criterio) {
        if (nota == null || nota < 1 || nota > 5) {
            throw new IllegalArgumentException("A nota de " + criterio + " deve estar entre 1 e 5.");
        }
    }
}
