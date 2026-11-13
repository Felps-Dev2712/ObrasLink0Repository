package com.obraslink.service;

import com.obraslink.model.Cliente;
import com.obraslink.model.ContaVinculada;
import com.obraslink.model.Usuario;
import com.obraslink.repository.ContaVinculadaRepository;
import com.obraslink.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ContaVinculadaServiceTest {
    ContaVinculadaRepository contas = mock(ContaVinculadaRepository.class);
    UsuarioRepository usuarios = mock(UsuarioRepository.class);
    ClienteService clientes = mock(ClienteService.class);
    PrestadorService prestadores = mock(PrestadorService.class);
    ContaVinculadaService service = new ContaVinculadaService(contas, usuarios, clientes, prestadores);
    Usuario usuario = Usuario.builder().id(7L).email("ana@exemplo.com").build();

    @BeforeEach
    void preparar() {
        when(usuarios.findById(7L)).thenReturn(Optional.of(usuario));
        when(clientes.findByUsuarioId(7L)).thenReturn(Optional.of(Cliente.builder().nome("Ana Cliente").build()));
        when(prestadores.findByUsuarioId(7L)).thenReturn(Optional.empty());
    }

    @Test
    void vincularGeraTokenQueAutenticaDeVoltaParaOMesmoUsuario() {
        var captor = org.mockito.ArgumentCaptor.forClass(ContaVinculada.class);
        String token = service.vincular(7L);
        verify(contas).save(captor.capture());
        ContaVinculada salvo = captor.getValue();
        assertNotNull(token);
        assertEquals(7L, salvo.getUsuarioId());
        assertNotEquals(token, salvo.getTokenHash(), "o hash salvo nunca é o token em texto puro");
        assertTrue(salvo.getExpiraEm().isAfter(LocalDateTime.now().plusDays(29)));

        when(contas.findByTokenHash(salvo.getTokenHash())).thenReturn(Optional.of(salvo));
        var autenticado = service.autenticar(token);
        assertTrue(autenticado.isPresent());
        assertEquals(usuario, autenticado.get());
    }

    @Test
    void tokenQueNaoExisteNaoAutentica() {
        when(contas.findByTokenHash(any())).thenReturn(Optional.empty());
        assertTrue(service.autenticar("qualquer-coisa").isEmpty());
    }

    @Test
    void tokenExpiradoNaoAutentica() {
        var expirado = ContaVinculada.builder().usuarioId(7L).tokenHash("h")
                .criadoEm(LocalDateTime.now().minusDays(40)).expiraEm(LocalDateTime.now().minusDays(10)).build();
        when(contas.findByTokenHash(any())).thenReturn(Optional.of(expirado));
        assertTrue(service.autenticar("token-velho").isEmpty());
        verifyNoInteractions(usuarios);
    }

    @Test
    void revogarRemoveOTokenPeloHash() {
        service.revogar("abc");
        verify(contas).deleteByTokenHash(any());
    }

    @Test
    void listarTrazNomeEmailFotoEMarcaAContaAtualComoAtiva() {
        var valido = ContaVinculada.builder().usuarioId(7L).tokenHash("h")
                .criadoEm(LocalDateTime.now()).expiraEm(LocalDateTime.now().plusDays(29)).build();
        when(contas.findByTokenHash(any())).thenReturn(Optional.of(valido));

        var resultado = service.listar(List.of("token-qualquer"), 7L);

        assertEquals(1, resultado.size());
        var conta = resultado.get(0);
        assertEquals("Ana Cliente", conta.nome());
        assertEquals("ana@exemplo.com", conta.email());
        assertTrue(conta.ativa());
        assertEquals("token-qualquer", conta.token());
    }

    @Test
    void listarIgnoraTokenExpiradoOuDeUsuarioInexistente() {
        var expirado = ContaVinculada.builder().usuarioId(7L).tokenHash("h1")
                .criadoEm(LocalDateTime.now()).expiraEm(LocalDateTime.now().minusDays(1)).build();
        when(contas.findByTokenHash(any())).thenReturn(Optional.of(expirado));
        assertTrue(service.listar(List.of("token-expirado"), 7L).isEmpty());
    }

    @Test
    void listarJuntaDoisTokensDoMesmoUsuarioNumaSoLinhaFicandoComOMaisRecente() {
        var primeiro = ContaVinculada.builder().usuarioId(7L).tokenHash("hash-token-velho")
                .criadoEm(LocalDateTime.now()).expiraEm(LocalDateTime.now().plusDays(29)).build();
        var segundo = ContaVinculada.builder().usuarioId(7L).tokenHash("hash-token-novo")
                .criadoEm(LocalDateTime.now()).expiraEm(LocalDateTime.now().plusDays(29)).build();
        when(contas.findByTokenHash(hash("token-velho"))).thenReturn(Optional.of(primeiro));
        when(contas.findByTokenHash(hash("token-novo"))).thenReturn(Optional.of(segundo));

        var resultado = service.listar(List.of("token-velho", "token-novo"), 99L);

        assertEquals(1, resultado.size(), "mesma conta, uma linha só");
        assertEquals("token-novo", resultado.get(0).token(), "fica com o token mais recente (cookie em ordem de uso)");
    }

    // mesmo algoritmo de hash do serviço, só para montar o cenário acima a partir do token em texto puro
    private static String hash(String tokenBruto) {
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256").digest(tokenBruto.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
