package com.obraslink.service;

import com.obraslink.model.Certificacao;
import com.obraslink.model.PapelUsuario;
import com.obraslink.model.Plano;
import com.obraslink.model.Prestador;
import com.obraslink.model.Servico;
import com.obraslink.model.Usuario;
import com.obraslink.repository.CertificacaoRepository;
import com.obraslink.repository.PrestadorRepository;
import com.obraslink.repository.ServicoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlanoServiceTest {
    private final AcessoService acesso = mock(AcessoService.class);
    private final PerfilAtivoService perfil = mock(PerfilAtivoService.class);
    private final PrestadorRepository prestadores = mock(PrestadorRepository.class);
    private final ServicoRepository servicos = mock(ServicoRepository.class);
    private final CertificacaoRepository certificacoes = mock(CertificacaoRepository.class);
    private final PlanoService service = new PlanoService(acesso, perfil, prestadores, servicos, certificacoes);
    private Prestador prestador;

    @BeforeEach
    void preparar() {
        Usuario usuario = Usuario.builder().id(7L).email("p@x.com").senha("x").ativo(true).papeis(Set.of(PapelUsuario.PRESTADOR)).build();
        prestador = Prestador.builder().id(30L).nome("Fornecedor").usuarioId(7L).plano(Plano.GRATUITO).build();
        when(acesso.getUsuarioAtualOrThrow()).thenReturn(usuario);
        when(perfil.atual()).thenReturn(PapelUsuario.PRESTADOR);
        when(prestadores.findByUsuarioId(7L)).thenReturn(Optional.of(prestador));
    }

    @Test
    void todosRetornaOsQuatroPlanosNaOrdemDoEnum() {
        assertEquals(List.of(Plano.GRATUITO, Plano.ESSENCIAL, Plano.PROFISSIONAL, Plano.EMPRESARIAL), service.todos());
    }

    @Test
    void prestadorAtualExigePerfilDePrestadorAtivo() {
        when(perfil.atual()).thenReturn(PapelUsuario.CLIENTE);
        assertThrows(IllegalArgumentException.class, service::prestadorAtual);
    }

    @Test
    void assinarTrocaOPlanoEGravaOPrestador() {
        Plano novo = service.assinar(Plano.PROFISSIONAL);
        assertEquals(Plano.PROFISSIONAL, novo);
        assertEquals(Plano.PROFISSIONAL, prestador.getPlano());
        verify(prestadores).save(prestador);
    }

    @Test
    void assinarRecusaPlanoNulo() {
        assertThrows(IllegalArgumentException.class, () -> service.assinar(null));
        verify(prestadores, never()).save(any());
    }

    @Test
    void exigirLimiteServicosRecusaQuandoAtingiuOLimiteDoPlano() {
        prestador.setPlano(Plano.GRATUITO); // limite 3
        when(servicos.findByPrestadorIdAndDisponivel(30L, true)).thenReturn(List.of(new Servico(), new Servico(), new Servico()));
        var ex = assertThrows(IllegalArgumentException.class, () -> service.exigirLimiteServicos(prestador));
        assertTrue(ex.getMessage().contains("Gratuito"));
    }

    @Test
    void exigirLimiteServicosLiberaAbaixoDoLimite() {
        prestador.setPlano(Plano.GRATUITO);
        when(servicos.findByPrestadorIdAndDisponivel(30L, true)).thenReturn(List.of(new Servico(), new Servico()));
        assertDoesNotThrow(() -> service.exigirLimiteServicos(prestador));
    }

    @Test
    void exigirLimiteServicosNuncaRecusaNoPlanoIlimitadoSemNemConsultarOBanco() {
        prestador.setPlano(Plano.EMPRESARIAL);
        assertDoesNotThrow(() -> service.exigirLimiteServicos(prestador));
        verifyNoInteractions(servicos);
    }

    @Test
    void exigirLimiteCertificacoesRecusaQuandoAtingiuOLimiteDoPlano() {
        when(certificacoes.findByPrestadorIdOrderByCriadoEmDesc(30L)).thenReturn(List.of(new Certificacao()));
        var ex = assertThrows(IllegalArgumentException.class,
                () -> service.exigirLimiteCertificacoes(30L, Plano.GRATUITO)); // limite 1
        assertTrue(ex.getMessage().contains("Gratuito"));
    }

    @Test
    void exigirLimiteCertificacoesLiberaAbaixoDoLimite() {
        when(certificacoes.findByPrestadorIdOrderByCriadoEmDesc(30L)).thenReturn(List.of());
        assertDoesNotThrow(() -> service.exigirLimiteCertificacoes(30L, Plano.GRATUITO));
    }
}
