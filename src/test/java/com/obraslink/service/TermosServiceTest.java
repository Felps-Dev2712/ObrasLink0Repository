package com.obraslink.service;

import com.obraslink.model.Usuario;
import com.obraslink.repository.AceiteTermosRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TermosServiceTest {
    private final AcessoService acesso = mock(AcessoService.class);
    private final AceiteTermosRepository repository = mock(AceiteTermosRepository.class);
    private final EntityManager em = mock(EntityManager.class);
    private final TermosService service = new TermosService(acesso, repository, em);

    @Test
    void exigeAmbasConfirmacoesExplicitas() {
        assertThrows(IllegalArgumentException.class, () -> service.aceitar(TermosService.VERSAO_ATUAL, false, true));
        assertThrows(IllegalArgumentException.class, () -> service.aceitar(TermosService.VERSAO_ATUAL, true, false));
        verifyNoInteractions(repository, em, acesso);
    }

    @Test
    void rejeitaFormularioDeVersaoAntiga() {
        assertThrows(IllegalArgumentException.class, () -> service.aceitar("antiga", true, true));
        verifyNoInteractions(repository, em, acesso);
    }

    @Test
    void registraUsuarioDaSessaoVersaoEDataDoServidor() {
        prepararUsuario();
        Instant inicio = Instant.now();
        service.aceitar(TermosService.VERSAO_ATUAL, true, true);
        verify(repository).save(argThat(aceite -> aceite.getUsuarioId().equals(7L)
                && aceite.getVersao().equals(TermosService.VERSAO_ATUAL)
                && !aceite.getAceitoEm().isBefore(inicio)));
    }

    @Test
    void repeticaoNaoAlteraDataNemDuplicaRegistro() {
        prepararUsuario();
        when(repository.existsByUsuarioIdAndVersao(7L, TermosService.VERSAO_ATUAL)).thenReturn(true);
        service.aceitar(TermosService.VERSAO_ATUAL, true, true);
        verify(repository, never()).save(any());
    }

    @Test
    void contaInativaNaoPodeAceitar() {
        prepararUsuario().setAtivo(false);
        assertThrows(IllegalArgumentException.class, () -> service.aceitar(TermosService.VERSAO_ATUAL, true, true));
        verifyNoInteractions(repository);
    }

    private Usuario prepararUsuario() {
        Usuario usuario = Usuario.builder().id(7L).build();
        when(acesso.getUsuarioAtualOrThrow()).thenReturn(usuario);
        when(em.find(Usuario.class, 7L, LockModeType.PESSIMISTIC_WRITE)).thenReturn(usuario);
        return usuario;
    }
}
