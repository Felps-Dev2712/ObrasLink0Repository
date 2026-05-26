package com.obraslink.service;

import com.obraslink.dto.AvaliacaoPrestadorForm;
import com.obraslink.model.AvaliacaoPrestador;
import com.obraslink.model.Cliente;
import com.obraslink.model.PapelUsuario;
import com.obraslink.model.Prestador;
import com.obraslink.model.Servico;
import com.obraslink.model.Usuario;
import com.obraslink.repository.AvaliacaoPrestadorRepository;
import com.obraslink.repository.ClienteRepository;
import com.obraslink.repository.PrestadorRepository;
import com.obraslink.repository.ServicoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AvaliacaoPrestadorService {

    private final AvaliacaoPrestadorRepository avaliacaoRepository;
    private final ClienteRepository clienteRepository;
    private final PrestadorRepository prestadorRepository;
    private final ServicoRepository servicoRepository;
    private final AcessoService acessoService;

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
    public void avaliar(Long prestadorId, AvaliacaoPrestadorForm form) {
        Usuario usuario = acessoService.getUsuarioAtualOrThrow();
        if (!usuario.getPapeis().contains(PapelUsuario.CLIENTE)) {
            throw new IllegalArgumentException("Apenas clientes podem registrar avaliacoes.");
        }

        Cliente cliente = clienteRepository.findByUsuarioId(usuario.getId())
                .orElseThrow(() -> new IllegalArgumentException("Seu usuario nao possui cadastro de cliente."));
        Prestador prestador = prestadorRepository.findById(prestadorId)
                .orElseThrow(() -> new IllegalArgumentException("Prestador nao encontrado."));
        Servico servico = servicoRepository.findById(form.getServicoId())
                .orElseThrow(() -> new IllegalArgumentException("Selecione o servico realizado para avaliar."));

        if (!servico.getPrestador().getId().equals(prestador.getId())) {
            throw new IllegalArgumentException("O servico selecionado nao pertence a este prestador.");
        }

        if (prestador.getUsuarioId() != null && prestador.getUsuarioId().equals(usuario.getId())) {
            throw new IllegalArgumentException("Voce nao pode avaliar seu proprio perfil de prestador.");
        }
        if (avaliacaoRepository.existsByClienteIdAndServicoId(cliente.getId(), servico.getId())) {
            throw new IllegalArgumentException("Voce ja registrou uma avaliacao para este servico.");
        }

        validarNota(form.getQualidade(), "qualidade");
        validarNota(form.getPrazo(), "prazo");
        validarNota(form.getPreco(), "preco");
        validarNota(form.getOrganizacao(), "organizacao");
        validarNota(form.getAtendimento(), "atendimento");

        AvaliacaoPrestador avaliacao = AvaliacaoPrestador.builder()
                .prestador(prestador)
                .cliente(cliente)
                .servico(servico)
                .qualidade(form.getQualidade())
                .prazo(form.getPrazo())
                .preco(form.getPreco())
                .organizacao(form.getOrganizacao())
                .atendimento(form.getAtendimento())
                .comentario(form.getComentario())
                .build();
        avaliacaoRepository.save(avaliacao);
    }

    private void validarNota(Integer nota, String criterio) {
        if (nota == null || nota < 1 || nota > 5) {
            throw new IllegalArgumentException("A nota de " + criterio + " deve estar entre 1 e 5.");
        }
    }
}
