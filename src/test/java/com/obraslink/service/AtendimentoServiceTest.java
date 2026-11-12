package com.obraslink.service;

import com.obraslink.model.*;
import com.obraslink.repository.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AtendimentoServiceTest {
    private final AcessoService acesso = mock(AcessoService.class);
    private final ClienteRepository clientes = mock(ClienteRepository.class);
    private final AtendimentoRepository repository = mock(AtendimentoRepository.class);
    private final NotificacaoService notificacoes = mock(NotificacaoService.class);
    private final EntityManager em = mock(EntityManager.class);
    private final AtendimentoService service = new AtendimentoService(acesso, clientes, repository, notificacoes, em);
    private Usuario usuario;
    private Prestador prestador;

    @BeforeEach
    void preparar() {
        usuario = Usuario.builder().id(1L).papeis(Set.of(PapelUsuario.CLIENTE)).build();
        prestador = Prestador.builder().id(10L).usuarioId(2L).nome("Fornecedor").whatsapp("(11) 99999-9999").build();
        when(acesso.getUsuarioAtualOrThrow()).thenReturn(usuario);
        when(em.find(Prestador.class, 10L, LockModeType.PESSIMISTIC_WRITE)).thenReturn(prestador);
        when(em.find(Usuario.class, 2L)).thenReturn(Usuario.builder().id(2L).build());
        when(clientes.findByUsuarioId(1L)).thenReturn(Optional.of(Cliente.builder().nome("Cliente").build()));
        when(repository.save(any(Atendimento.class))).thenAnswer(invocation -> {
            Atendimento atendimento = invocation.getArgument(0);
            atendimento.setId(20L);
            return atendimento;
        });
    }

    @Test
    void registraIdentidadesENotificaAntesDeAbrirWhatsapp() {
        assertTrue(service.contatar(10L).startsWith("https://wa.me/5511999999999?text="));
        verify(repository).save(argThat(a -> a.getClienteUsuarioId().equals(1L)
                && a.getPrestadorUsuarioId().equals(2L) && a.getPrestadorId().equals(10L)));
        verify(notificacoes).criar(eq(2L), contains("Cliente"), eq("/notificacoes"));
    }

    @Test
    void mensagemDoWhatsappNaoExpoeONumeroInternoDoAtendimento() {
        String url = service.contatar(10L);
        String mensagem = java.net.URLDecoder.decode(url.substring(url.indexOf("text=") + 5), java.nio.charset.StandardCharsets.UTF_8);
        assertFalse(mensagem.contains("#"), mensagem);
        assertFalse(mensagem.toLowerCase().contains("atendimento"), mensagem);
        assertTrue(mensagem.contains("Vim pelo ObraLink"));
    }

    @Test
    void cliqueRepetidoReutilizaAtendimentoAberto() {
        when(repository.findFirstByClienteUsuarioIdAndPrestadorIdAndStatusNotOrderByIdDesc(1L, 10L, Atendimento.Status.CONCLUIDO))
                .thenReturn(Optional.of(new Atendimento()));
        service.contatar(10L);
        verify(repository, never()).save(any());
        verifyNoInteractions(notificacoes);
    }

    @Test
    void rejeitaPerfilProprioEWhatsappInvalido() {
        prestador.setUsuarioId(1L);
        when(em.find(Usuario.class, 1L)).thenReturn(usuario);
        assertThrows(IllegalArgumentException.class, () -> service.contatar(10L));
        prestador.setUsuarioId(2L);
        prestador.setWhatsapp("123");
        assertThrows(IllegalArgumentException.class, () -> service.contatar(10L));
        verifyNoInteractions(repository, notificacoes);
    }

    @Test
    void somenteFornecedorResponsavelPodeAlterar() {
        Atendimento atendimento = atendimento();
        assertThrows(IllegalArgumentException.class, () -> service.atualizar(20L, false));
        assertEquals(Atendimento.Status.CONTATO_SOLICITADO, atendimento.getStatus());
        verifyNoInteractions(notificacoes);
    }

    private static final java.time.ZoneId SP = java.time.ZoneId.of("America/Sao_Paulo");

    private void agora(String dataHora) {
        service.usarRelogio(java.time.Clock.fixed(java.time.LocalDateTime.parse(dataHora).atZone(SP).toInstant(), SP));
    }

    @Test
    void exigeConfirmacaoAntesDeConcluirENotificaUmaVezPorEtapa() {
        agora("2026-11-12T10:00");
        Atendimento atendimento = atendimento();
        usuario.setId(2L);
        assertThrows(IllegalArgumentException.class, () -> service.atualizar(20L, true));
        var data = java.time.LocalDate.of(2026, 11, 13);
        service.atualizar(20L, false, data, java.time.LocalTime.of(14, 30));
        service.atualizar(20L, false, data, java.time.LocalTime.of(14, 30));
        assertNotNull(atendimento.getConfirmadoEm());
        assertEquals(data,atendimento.getDataAgendada());assertEquals(java.time.LocalTime.of(14,30),atendimento.getHorarioAgendado());
        assertEquals(Atendimento.Status.EM_ANDAMENTO, atendimento.getStatus());
        agora("2026-11-13T15:00"); // conclusão só a partir do dia agendado
        service.atualizar(20L, true);
        service.atualizar(20L, true);
        assertNotNull(atendimento.getConcluidoEm());
        assertEquals(Atendimento.Status.CONCLUIDO, atendimento.getStatus());
        assertThrows(IllegalArgumentException.class, () -> service.atualizar(20L, false));
        verify(notificacoes, times(2)).criar(eq(1L), anyString(), eq("/notificacoes"));
    }

    private Atendimento emAndamento(java.time.LocalDate data, java.time.LocalTime horario) {
        Atendimento atendimento = atendimento();
        atendimento.setStatus(Atendimento.Status.EM_ANDAMENTO);
        atendimento.setDataAgendada(data);
        atendimento.setHorarioAgendado(horario);
        usuario.setId(2L);
        return atendimento;
    }

    @Test
    void naoConcluiAntesDoDiaAgendadoEExplicaComoResolver() {
        agora("2026-11-12T10:00");
        Atendimento atendimento = emAndamento(java.time.LocalDate.of(2026, 11, 22), java.time.LocalTime.of(23, 59));
        var erro = assertThrows(IllegalArgumentException.class, () -> service.atualizar(20L, true));
        assertTrue(erro.getMessage().contains("22/11/2026 às 23:59") && erro.getMessage().contains("altere a data"), erro.getMessage());
        assertEquals(Atendimento.Status.EM_ANDAMENTO, atendimento.getStatus());
        assertNull(atendimento.getConcluidoEm());
        verifyNoInteractions(notificacoes);
    }

    @Test
    void concluiNoProprioDiaAgendadoMesmoAntesDoHorario() {
        agora("2026-11-22T08:00");
        Atendimento atendimento = emAndamento(java.time.LocalDate.of(2026, 11, 22), java.time.LocalTime.of(23, 59));
        service.atualizar(20L, true);
        assertEquals(Atendimento.Status.CONCLUIDO, atendimento.getStatus());
        verify(notificacoes).criar(eq(1L), contains("concluído"), eq("/notificacoes"));
    }

    @Test
    void confirmacaoAvisaOClienteComDataNoFormatoBrasileiro() {
        agora("2026-11-12T10:00");
        atendimento();
        usuario.setId(2L);
        service.atualizar(20L, false, java.time.LocalDate.of(2026, 11, 22), java.time.LocalTime.of(23, 59));
        verify(notificacoes).criar(eq(1L), eq("Fornecedor confirmou a contratação para 22/11/2026 às 23:59."), eq("/notificacoes"));
    }

    @Test
    void reagendaServicoEmAndamentoEAvisaOCliente() {
        agora("2026-11-12T10:00");
        Atendimento atendimento = emAndamento(java.time.LocalDate.of(2026, 11, 22), java.time.LocalTime.of(23, 59));
        service.reagendar(20L, java.time.LocalDate.of(2026, 11, 12), java.time.LocalTime.of(16, 0));
        assertEquals(java.time.LocalDate.of(2026, 11, 12), atendimento.getDataAgendada());
        assertEquals(java.time.LocalTime.of(16, 0), atendimento.getHorarioAgendado());
        assertEquals(Atendimento.Status.EM_ANDAMENTO, atendimento.getStatus());
        verify(notificacoes).criar(eq(1L), eq("Fornecedor alterou a data do seu serviço para 12/11/2026 às 16:00."), eq("/notificacoes"));
        service.atualizar(20L, true); // com a data de hoje, já pode concluir
        assertEquals(Atendimento.Status.CONCLUIDO, atendimento.getStatus());
    }

    @Test
    void reagendamentoRecusaDataOuHorarioNoPassadoECamposVazios() {
        agora("2026-11-12T10:00");
        Atendimento atendimento = emAndamento(java.time.LocalDate.of(2026, 11, 22), java.time.LocalTime.of(23, 59));
        assertThrows(IllegalArgumentException.class, () -> service.reagendar(20L, java.time.LocalDate.of(2026, 11, 11), java.time.LocalTime.of(16, 0)));
        var erro = assertThrows(IllegalArgumentException.class, () -> service.reagendar(20L, java.time.LocalDate.of(2026, 11, 12), java.time.LocalTime.of(9, 30)));
        assertTrue(erro.getMessage().contains("horário"), erro.getMessage());
        assertThrows(IllegalArgumentException.class, () -> service.reagendar(20L, null, java.time.LocalTime.of(16, 0)));
        assertThrows(IllegalArgumentException.class, () -> service.reagendar(20L, java.time.LocalDate.of(2026, 11, 23), null));
        assertThrows(IllegalArgumentException.class, () -> service.reagendar(20L, java.time.LocalDate.of(2026, 11, 22), java.time.LocalTime.of(23, 59)),
                "mesma data e horário");
        assertEquals(java.time.LocalDate.of(2026, 11, 22), atendimento.getDataAgendada());
        verifyNoInteractions(notificacoes);
    }

    @Test
    void reagendamentoSoParaOPrestadorResponsavelEComServicoEmAndamento() {
        agora("2026-11-12T10:00");
        Atendimento atendimento = emAndamento(java.time.LocalDate.of(2026, 11, 22), java.time.LocalTime.of(23, 59));
        usuario.setId(99L);
        assertThrows(IllegalArgumentException.class, () -> service.reagendar(20L, java.time.LocalDate.of(2026, 11, 20), java.time.LocalTime.of(10, 0)));
        usuario.setId(2L);
        atendimento.setStatus(Atendimento.Status.CONCLUIDO);
        assertThrows(IllegalArgumentException.class, () -> service.reagendar(20L, java.time.LocalDate.of(2026, 11, 20), java.time.LocalTime.of(10, 0)));
        atendimento.setStatus(Atendimento.Status.CONTATO_SOLICITADO);
        assertThrows(IllegalArgumentException.class, () -> service.reagendar(20L, java.time.LocalDate.of(2026, 11, 20), java.time.LocalTime.of(10, 0)));
        verifyNoInteractions(notificacoes);
    }

    private Atendimento atendimento() {
        Atendimento atendimento = new Atendimento();
        atendimento.setClienteUsuarioId(1L);
        atendimento.setPrestadorUsuarioId(2L);
        atendimento.setPrestadorNome("Fornecedor");
        when(em.find(Atendimento.class, 20L, LockModeType.PESSIMISTIC_WRITE)).thenReturn(atendimento);
        return atendimento;
    }
}
