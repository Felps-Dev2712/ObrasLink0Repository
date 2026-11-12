package com.obraslink.service;

import com.obraslink.model.AvaliacaoPrestador;
import com.obraslink.model.Servico;
import com.obraslink.model.ServicoFoto;
import com.obraslink.repository.AvaliacaoPrestadorRepository;
import com.obraslink.repository.ServicoFotoRepository;
import com.obraslink.repository.ServicoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

/**
 * Página pública de detalhes de um serviço: o serviço, todas as fotos, as avaliações daquele serviço
 * e o resumo de avaliações do prestador. Tudo em consultas fixas (sem N+1) e somente leitura.
 */
@Service
@RequiredArgsConstructor
public class ServicoDetalheService {
    private final ServicoRepository servicos;
    private final ServicoFotoRepository fotos;
    private final AvaliacaoPrestadorRepository avaliacoes;

    public record Detalhe(Servico servico, List<ServicoFoto> fotos, List<AvaliacaoPrestador> avaliacoes,
                          BigDecimal mediaServico, long totalAvaliacoesPrestador, BigDecimal mediaPrestador) { }

    @Transactional(readOnly = true)
    public Optional<Detalhe> carregar(Long servicoId) {
        if (servicoId == null) return Optional.empty();
        return servicos.findPublicoPorId(servicoId).map(servico -> {
            Long prestadorId = servico.getPrestador().getId();
            List<AvaliacaoPrestador> doServico = avaliacoes.findDoServico(prestadorId, servicoId);
            Object[] resumo = primeiraLinha(avaliacoes.resumoDoPrestador(prestadorId));
            long total = resumo == null || resumo[0] == null ? 0 : ((Number) resumo[0]).longValue();
            BigDecimal mediaPrestador = total == 0 || resumo[1] == null ? null : arredondar(((Number) resumo[1]).doubleValue());
            return new Detalhe(servico, fotos.findByServicoIdOrderByIdAsc(servicoId), doServico,
                    media(doServico), total, mediaPrestador);
        });
    }

    /** Média das notas (1 a 5) das avaliações informadas, com uma casa decimal; null se não houver nenhuma. */
    static BigDecimal media(List<AvaliacaoPrestador> lista) {
        if (lista == null || lista.isEmpty()) return null;
        double media = lista.stream()
                .mapToDouble(a -> (a.getQualidade() + a.getPrazo() + a.getPreco() + a.getOrganizacao() + a.getAtendimento()) / 5.0)
                .average().orElse(0);
        return arredondar(media);
    }

    private static BigDecimal arredondar(double valor) {
        return BigDecimal.valueOf(valor).setScale(1, RoundingMode.HALF_UP);
    }

    private static Object[] primeiraLinha(List<Object[]> linhas) {
        return linhas == null || linhas.isEmpty() ? null : linhas.get(0);
    }
}
