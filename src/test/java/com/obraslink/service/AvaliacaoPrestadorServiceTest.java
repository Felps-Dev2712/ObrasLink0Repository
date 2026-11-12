package com.obraslink.service;

import com.obraslink.dto.AvaliacaoPrestadorForm;
import com.obraslink.model.*;
import com.obraslink.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.util.Optional;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AvaliacaoPrestadorServiceTest {
    private final AvaliacaoPrestadorRepository avaliacoes = mock(AvaliacaoPrestadorRepository.class);
    private final ClienteRepository clientes = mock(ClienteRepository.class);
    private final PrestadorRepository prestadores = mock(PrestadorRepository.class);
    private final AtendimentoRepository atendimentos = mock(AtendimentoRepository.class);
    private final AcessoService acesso = mock(AcessoService.class);
    private final NotificacaoService notificacoes = mock(NotificacaoService.class);
    private final AvaliacaoPrestadorService service = new AvaliacaoPrestadorService(avaliacoes, clientes, prestadores, atendimentos, acesso, notificacoes);
    private final Atendimento contratado = new Atendimento();
    private final AvaliacaoPrestadorForm form = new AvaliacaoPrestadorForm();
    private Usuario usuario;

    @BeforeEach
    void preparar() {
        usuario = Usuario.builder().id(10L).papeis(Set.of(PapelUsuario.CLIENTE)).build();
        when(acesso.getUsuarioAtualOrThrow()).thenReturn(usuario);
        when(acesso.getUsuarioAtual()).thenReturn(Optional.of(usuario));
        when(clientes.findByUsuarioId(10L)).thenReturn(Optional.of(Cliente.builder().id(20L).nome("Cliente").build()));
        when(prestadores.findById(30L)).thenReturn(Optional.of(Prestador.builder().id(30L).usuarioId(40L).nome("Fornecedor").build()));
        contratado.setId(50L);
        contratado.setClienteUsuarioId(10L);
        contratado.setPrestadorId(30L);
        contratado.setPrestadorUsuarioId(40L);
        contratado.setStatus(Atendimento.Status.CONCLUIDO);
        when(atendimentos.findByIdParaAvaliar(50L)).thenReturn(Optional.of(contratado));

        form.setQualidade(5);
        form.setPrazo(4);
        form.setPreco(3);
        form.setOrganizacao(2);
        form.setAtendimento(1);
    }

    @Test
    void registraNotasIndependentesVinculadasAoAtendimentoConcluido() {
        service.avaliar(30L, 50L, form);
        verify(avaliacoes).save(argThat(a -> a.getAtendimentoId().equals(50L)
                && a.getQualidade() == 5 && a.getPrazo() == 4 && a.getPreco() == 3
                && a.getOrganizacao() == 2 && a.getAtendimento() == 1 && a.getServico() == null));
        // Cliente vai para o perfil; o prestador vai direto para a avaliação que recebeu
        verify(notificacoes).criar(eq(10L), anyString(), eq("/prestadores/30"));
        verify(notificacoes).criar(eq(40L), contains("nova avaliação"), eq("/atendimentos/50/avaliacao-profissional"));
    }

    @Test
    void prestadorVeAAvaliacaoRecebidaDoAtendimentoComMedia() {
        usuario.setId(40L); // o prestador do atendimento
        when(atendimentos.findById(50L)).thenReturn(Optional.of(contratado));
        AvaliacaoPrestador recebida = AvaliacaoPrestador.builder().atendimentoId(50L).qualidade(5).prazo(4).preco(5).organizacao(4).atendimento(5).build();
        when(avaliacoes.findDetalheByAtendimentoId(50L)).thenReturn(Optional.of(recebida));

        var r = service.consultarRecebida(50L);
        assertTrue(r.souPrestador());
        assertSame(recebida, r.avaliacao());
        assertEquals(new java.math.BigDecimal("4.6"), r.media());
    }

    @Test
    void clienteQueAvaliouTambemPodeVerMasTerceirosNao() {
        when(atendimentos.findById(50L)).thenReturn(Optional.of(contratado));
        when(avaliacoes.findDetalheByAtendimentoId(50L)).thenReturn(Optional.empty());
        var r = service.consultarRecebida(50L); // usuário 10 = cliente
        assertFalse(r.souPrestador());
        assertNull(r.avaliacao());
        assertNull(r.media());

        usuario.setId(99L);
        assertThrows(IllegalArgumentException.class, () -> service.consultarRecebida(50L));
        when(atendimentos.findById(77L)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> service.consultarRecebida(77L));
    }

    @Test
    void listaQuaisAtendimentosJaForamAvaliadosPeloClienteNumaConsulta() {
        when(avaliacoes.findAtendimentoIdsAvaliados(java.util.List.of(50L, 51L))).thenReturn(java.util.List.of(51L));
        assertEquals(Set.of(51L), service.atendimentosComAvaliacaoDoCliente(java.util.List.of(50L, 51L)));
        assertTrue(service.atendimentosComAvaliacaoDoCliente(java.util.List.of()).isEmpty());
        verify(avaliacoes, times(1)).findAtendimentoIdsAvaliados(any());
    }

    @Test
    void recusaComentarioAcimaDe2000CaracteresSemGravar() {
        form.setComentario("a".repeat(2001));
        var erro = assertThrows(IllegalArgumentException.class, () -> service.avaliar(30L, 50L, form));
        assertEquals("O comentário deve ter no máximo 2.000 caracteres.", erro.getMessage());
        verify(avaliacoes, never()).save(any());
        verifyNoInteractions(notificacoes);
    }

    @Test
    void gravaComentarioNormalizadoNoLimite() {
        form.setComentario("  " + "a".repeat(1000) + "\r\n" + "b".repeat(999) + "  ");
        service.avaliar(30L, 50L, form);
        verify(avaliacoes).save(argThat(a -> a.getComentario().length() == 2000 && !a.getComentario().contains("\r")));
    }

    @Test
    void comentarioEmBrancoViraNulo() {
        form.setComentario("   ");
        service.avaliar(30L, 50L, form);
        verify(avaliacoes).save(argThat(a -> a.getComentario() == null));
    }

    @ParameterizedTest
    @EnumSource(value = Atendimento.Status.class, names = {"CONTATO_SOLICITADO", "EM_ANDAMENTO"})
    void naoPermiteAvaliarAntesDaConclusao(Atendimento.Status status) {
        contratado.setStatus(status);
        rejeitar();
    }

    @Test void naoPermiteAtendimentoDeOutroCliente() { contratado.setClienteUsuarioId(99L); rejeitar(); }
    @Test void naoPermiteAtendimentoDeOutroPrestador() { contratado.setPrestadorId(99L); rejeitar(); }
    @Test void naoPermiteContaDeFornecedorDiferente() { contratado.setPrestadorUsuarioId(99L); rejeitar(); }
    @Test void naoPermiteSemContratacao() { when(atendimentos.findByIdParaAvaliar(50L)).thenReturn(Optional.empty()); rejeitar(); }
    @Test void recusaAtendimentoInexistenteRecebidoPeloContexto() { when(atendimentos.findByIdParaAvaliar(999L)).thenReturn(Optional.empty()); assertThrows(IllegalArgumentException.class, () -> service.avaliar(30L,999L,form)); }
    @Test void naoPermiteRepetirAvaliacao() { when(avaliacoes.existsByAtendimentoId(50L)).thenReturn(true); rejeitar(); }
    @Test void exigeNotaEmTodosOsCriterios() { form.setPrazo(null); rejeitar(); }
    @Test void rejeitaNotaForaDaEscala() { form.setOrganizacao(6); rejeitar(); }
    @Test void naoPermiteContaSemPerfilCliente() { usuario.setPapeis(Set.of(PapelUsuario.PRESTADOR)); rejeitar(); }
    @Test void naoPermiteContaInativa() { usuario.setAtivo(false); rejeitar(); }

    @Test
    void naoPermiteAvaliarProprioPerfilMesmoComContratacao() {
        when(prestadores.findById(30L)).thenReturn(Optional.of(Prestador.builder().id(30L).usuarioId(10L).build()));
        contratado.setPrestadorUsuarioId(10L);
        rejeitar();
    }

    @Test
    void formularioComecaSemNotasPreSelecionadas() {
        var novo = new AvaliacaoPrestadorForm();
        assertNull(novo.getQualidade());
        assertNull(novo.getPrazo());
        assertNull(novo.getPreco());
        assertNull(novo.getOrganizacao());
        assertNull(novo.getAtendimento());
    }

    @Test
    void listaSomenteAtendimentosConcluidosDoClienteLogado() {
        service.atendimentosDisponiveis(30L);
        verify(atendimentos).findDisponiveisParaAvaliar(10L, 30L, Atendimento.Status.CONCLUIDO);
        when(acesso.getUsuarioAtual()).thenReturn(Optional.empty());
        assertTrue(service.atendimentosDisponiveis(30L).isEmpty());
    }

    private void rejeitar() {
        assertThrows(IllegalArgumentException.class, () -> service.avaliar(30L,50L,form));
        verify(avaliacoes, never()).save(any());
        verifyNoInteractions(notificacoes);
    }
}
