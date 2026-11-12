package com.obraslink.service;

import com.obraslink.model.*;
import com.obraslink.repository.AvaliacaoPrestadorRepository;
import com.obraslink.repository.ServicoFotoRepository;
import com.obraslink.repository.ServicoRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ServicoDetalheServiceTest {
    private final ServicoRepository servicos = mock(ServicoRepository.class);
    private final ServicoFotoRepository fotos = mock(ServicoFotoRepository.class);
    private final AvaliacaoPrestadorRepository avaliacoes = mock(AvaliacaoPrestadorRepository.class);
    private final ServicoDetalheService service = new ServicoDetalheService(servicos, fotos, avaliacoes);

    private static AvaliacaoPrestador avaliacao(int nota) {
        return AvaliacaoPrestador.builder().qualidade(nota).prazo(nota).preco(nota).organizacao(nota).atendimento(nota).build();
    }

    private static List<Object[]> linha(Object quantidade, Object media) {
        List<Object[]> linhas = new ArrayList<>();
        linhas.add(new Object[]{quantidade, media});
        return linhas;
    }

    @Test
    void carregaServicoFotosAvaliacoesDoServicoEResumoDoPrestador() {
        Prestador prestador = Prestador.builder().id(30L).nome("Fornecedor").build();
        Servico servico = Servico.builder().id(5L).titulo("Pintura").prestador(prestador).disponivel(true).build();
        when(servicos.findPublicoPorId(5L)).thenReturn(Optional.of(servico));
        when(fotos.findByServicoIdOrderByIdAsc(5L)).thenReturn(List.of(new ServicoFoto(5L, "a.jpg"), new ServicoFoto(5L, "b.jpg")));
        when(avaliacoes.findDoServico(30L, 5L)).thenReturn(List.of(avaliacao(5), avaliacao(4)));
        when(avaliacoes.resumoDoPrestador(30L)).thenReturn(linha(8L, 4.1234));

        var d = service.carregar(5L).orElseThrow();
        assertSame(servico, d.servico());
        assertEquals(2, d.fotos().size());
        assertEquals(2, d.avaliacoes().size());
        assertEquals(new BigDecimal("4.5"), d.mediaServico());
        assertEquals(8, d.totalAvaliacoesPrestador());
        assertEquals(new BigDecimal("4.1"), d.mediaPrestador());
    }

    @Test
    void semAvaliacoesAMediaFicaVaziaEOResumoZerado() {
        Servico servico = Servico.builder().id(5L).prestador(Prestador.builder().id(30L).build()).build();
        when(servicos.findPublicoPorId(5L)).thenReturn(Optional.of(servico));
        when(avaliacoes.findDoServico(30L, 5L)).thenReturn(List.of());
        when(avaliacoes.resumoDoPrestador(30L)).thenReturn(linha(0L, null));

        var d = service.carregar(5L).orElseThrow();
        assertNull(d.mediaServico());
        assertEquals(0, d.totalAvaliacoesPrestador());
        assertNull(d.mediaPrestador());
    }

    @Test
    void servicoInexistenteOuDeContaInativaNaoCarregaNada() {
        when(servicos.findPublicoPorId(99L)).thenReturn(Optional.empty());
        assertTrue(service.carregar(99L).isEmpty());
        assertTrue(service.carregar(null).isEmpty());
        verifyNoInteractions(fotos, avaliacoes);
    }
}
