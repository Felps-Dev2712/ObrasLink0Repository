package com.obraslink.service;

import com.obraslink.dto.DenunciaForm;
import com.obraslink.model.*;
import com.obraslink.repository.AtendimentoRepository;
import com.obraslink.repository.DenunciaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DenunciaServiceTest {
    AtendimentoRepository atendimentos = mock(AtendimentoRepository.class);
    DenunciaRepository denuncias = mock(DenunciaRepository.class);
    AcessoService acesso = mock(AcessoService.class);
    DenunciaService service = new DenunciaService(atendimentos, denuncias, acesso);

    Atendimento atendimento;
    Usuario usuario;
    DenunciaForm form;

    @BeforeEach
    void preparar() {
        usuario = Usuario.builder().id(10L).ativo(true).papeis(Set.of(PapelUsuario.CLIENTE)).build();
        when(acesso.getUsuarioAtualOrThrow()).thenReturn(usuario);
        atendimento = new Atendimento();
        atendimento.setId(50L);
        atendimento.setClienteUsuarioId(10L);
        atendimento.setClienteNome("Camila");
        atendimento.setPrestadorUsuarioId(40L);
        atendimento.setPrestadorNome("Rafael");
        atendimento.setPrestadorId(30L);
        when(atendimentos.findById(50L)).thenReturn(Optional.of(atendimento));
        form = new DenunciaForm();
        form.setMotivo(Denuncia.Motivo.COMPORTAMENTO_INADEQUADO);
        form.setDescricao("O profissional foi grosseiro e não terminou o combinado.");
    }

    private void rejeita() {
        assertThrows(IllegalArgumentException.class, () -> service.denunciar(50L, form));
        verify(denuncias, never()).save(any());
    }

    @Test
    void clienteDenunciaOPrestadorDoAtendimento() {
        service.denunciar(50L, form);
        verify(denuncias).save(argThat(d -> d.getAtendimentoId() == 50L && d.getAutorUsuarioId() == 10L
                && d.getDenunciadoUsuarioId() == 40L && d.getAutorNome().equals("Camila") && d.getDenunciadoNome().equals("Rafael")
                && d.getMotivo() == Denuncia.Motivo.COMPORTAMENTO_INADEQUADO && d.getEstado() == Denuncia.Estado.PENDENTE));
    }

    @Test
    void prestadorDenunciaOClienteDoAtendimento() {
        usuario.setId(40L);
        usuario.setPapeis(Set.of(PapelUsuario.PRESTADOR));
        service.denunciar(50L, form);
        verify(denuncias).save(argThat(d -> d.getAutorUsuarioId() == 40L && d.getDenunciadoUsuarioId() == 10L
                && d.getAutorNome().equals("Rafael") && d.getDenunciadoNome().equals("Camila")));
    }

    @Test
    void bloqueiaQuemNaoParticipouDoAtendimento() {
        usuario.setId(99L);
        rejeita();
        assertThrows(IllegalArgumentException.class, () -> service.consultar(50L));
    }

    @Test
    void bloqueiaContaInativa() {
        usuario.setAtivo(false);
        rejeita();
    }

    @Test
    void bloqueiaSemMotivo() {
        form.setMotivo(null);
        rejeita();
    }

    @Test
    void bloqueiaDescricaoVaziaOuSoComEspacos() {
        form.setDescricao("   ");
        rejeita();
    }

    @Test
    void bloqueiaDescricaoLonga() {
        form.setDescricao("a".repeat(2001));
        rejeita();
    }

    @Test
    void bloqueiaSegundaDenunciaDoMesmoAutorParaOMesmoAtendimento() {
        when(denuncias.findByAtendimentoIdAndAutorUsuarioId(50L, 10L)).thenReturn(Optional.of(new Denuncia()));
        rejeita();
        assertFalse(service.consultar(50L).podeDenunciar());
    }

    @Test
    void bloqueiaAtendimentoInexistente() {
        when(atendimentos.findById(50L)).thenReturn(Optional.empty());
        rejeita();
    }

    @Test
    void atendimentosJaDenunciadosConsultaUmaVezSoEListaVaziaNaoVaiAoBanco() {
        when(denuncias.findAtendimentoIdsDenunciados(List.of(31L, 32L), 10L)).thenReturn(List.of(32L));
        assertEquals(Set.of(32L), service.atendimentosJaDenunciados(List.of(31L, 32L), 10L));
        assertTrue(service.atendimentosJaDenunciados(List.of(), 10L).isEmpty());
        assertTrue(service.atendimentosJaDenunciados(null, 10L).isEmpty());
        verify(denuncias, times(1)).findAtendimentoIdsDenunciados(any(), any());
    }

    @Test
    void apenasAdminPodeListarPendentes() {
        assertThrows(IllegalArgumentException.class, () -> service.pendentes());
        usuario.setPapeis(Set.of(PapelUsuario.ADMIN));
        when(denuncias.findByEstadoOrderByCriadoEmAsc(Denuncia.Estado.PENDENTE)).thenReturn(List.of());
        assertTrue(service.pendentes().isEmpty());
    }

    @Test
    void analisarExigeAdminEEstadoDiferenteDePendente() {
        var d = Denuncia.builder().id(1L).estado(Denuncia.Estado.PENDENTE).build();
        when(denuncias.findById(1L)).thenReturn(Optional.of(d));
        assertThrows(IllegalArgumentException.class, () -> service.analisar(1L, Denuncia.Estado.PROCEDENTE, null));

        usuario.setPapeis(Set.of(PapelUsuario.ADMIN));
        assertThrows(IllegalArgumentException.class, () -> service.analisar(1L, Denuncia.Estado.PENDENTE, null));

        service.analisar(1L, Denuncia.Estado.PROCEDENTE, "Confirmado com o profissional.");
        assertEquals(Denuncia.Estado.PROCEDENTE, d.getEstado());
        assertEquals("Confirmado com o profissional.", d.getObservacaoAnalise());
        assertNotNull(d.getAnalisadoEm());
    }

    @Test
    void naoAnalisaDenunciaJaAnalisada() {
        usuario.setPapeis(Set.of(PapelUsuario.ADMIN));
        var d = Denuncia.builder().id(1L).estado(Denuncia.Estado.PROCEDENTE).build();
        when(denuncias.findById(1L)).thenReturn(Optional.of(d));
        assertThrows(IllegalArgumentException.class, () -> service.analisar(1L, Denuncia.Estado.IMPROCEDENTE, null));
    }
}
