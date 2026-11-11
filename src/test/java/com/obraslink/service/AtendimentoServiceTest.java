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

    @Test
    void exigeConfirmacaoAntesDeConcluirENotificaUmaVezPorEtapa() {
        Atendimento atendimento = atendimento();
        usuario.setId(2L);
        assertThrows(IllegalArgumentException.class, () -> service.atualizar(20L, true));
        var data = java.time.LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo")).plusDays(1);
        service.atualizar(20L, false, data, java.time.LocalTime.of(14, 30));
        service.atualizar(20L, false, data, java.time.LocalTime.of(14, 30));
        assertNotNull(atendimento.getConfirmadoEm());
        assertEquals(data,atendimento.getDataAgendada());assertEquals(java.time.LocalTime.of(14,30),atendimento.getHorarioAgendado());
        assertEquals(Atendimento.Status.EM_ANDAMENTO, atendimento.getStatus());
        service.atualizar(20L, true);
        service.atualizar(20L, true);
        assertNotNull(atendimento.getConcluidoEm());
        assertEquals(Atendimento.Status.CONCLUIDO, atendimento.getStatus());
        assertThrows(IllegalArgumentException.class, () -> service.atualizar(20L, false));
        verify(notificacoes, times(2)).criar(eq(1L), anyString(), eq("/notificacoes"));
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
