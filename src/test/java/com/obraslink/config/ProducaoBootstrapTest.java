package com.obraslink.config;

import com.obraslink.model.PapelUsuario;
import com.obraslink.model.Usuario;
import com.obraslink.repository.CategoriaRepository;
import com.obraslink.repository.UsuarioRepository;
import com.obraslink.service.ContaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class ProducaoBootstrapTest {

    static final String SENHA_FORTE = "uma-senha-forte-de-producao";

    UsuarioRepository usuarios = mock(UsuarioRepository.class);
    CategoriaRepository categorias = mock(CategoriaRepository.class);
    ContaService contaService = mock(ContaService.class);
    PasswordEncoder encoder = new BCryptPasswordEncoder(4);

    @BeforeEach
    void preparar() {
        when(usuarios.findByEmail(anyString())).thenReturn(Optional.empty());
        when(categorias.count()).thenReturn(8L);
    }

    ProducaoBootstrap bootstrap(String senha) {
        return new ProducaoBootstrap(usuarios, categorias, contaService, encoder, senha);
    }

    Usuario conta(long id, String email, String senha, PapelUsuario papel) {
        Usuario u = Usuario.builder().id(id).email(email).nome(email).senha(encoder.encode(senha))
                .papeis(new HashSet<>(Set.of(papel))).ativo(true).build();
        when(usuarios.findByEmail(email)).thenReturn(Optional.of(u));
        return u;
    }

    @Test
    void listaDeDemonstracaoTemTodasAsContasMenosOAdministrador() {
        assertEquals(21, DataSeeder.EMAILS_DEMONSTRACAO.size());
        assertEquals(21, new HashSet<>(DataSeeder.EMAILS_DEMONSTRACAO).size(), "e-mails repetidos");
        assertFalse(DataSeeder.EMAILS_DEMONSTRACAO.contains(DataSeeder.EMAIL_ADMIN));
        assertTrue(DataSeeder.EMAILS_DEMONSTRACAO.containsAll(Set.of(DataSeeder.EMAIL_CLIENTE_TESTE,
                DataSeeder.EMAIL_PRESTADOR_TESTE, DataSeeder.EMAIL_AMBOS_TESTE,
                "joao.pedreiro@obraslink.com", "campinas.obras@obraslink.com", "igor.cliente@obraslink.com")));
    }

    @Test
    void excluiAsContasDeDemonstracaoExistentesEMantemOAdministrador() {
        Usuario cliente = conta(2, DataSeeder.EMAIL_CLIENTE_TESTE, DataSeeder.SENHA_TESTE, PapelUsuario.CLIENTE);
        Usuario prestador = conta(3, "joao.pedreiro@obraslink.com", DataSeeder.SENHA_TESTE, PapelUsuario.PRESTADOR);
        Usuario admin = conta(1, DataSeeder.EMAIL_ADMIN, SENHA_FORTE, PapelUsuario.ADMIN);
        Usuario real = conta(50, "pessoa.real@example.com", "qualquer", PapelUsuario.CLIENTE);

        bootstrap(SENHA_FORTE).run();

        verify(contaService).excluirConta(cliente);
        verify(contaService).excluirConta(prestador);
        verify(contaService, never()).excluirConta(admin);
        verify(contaService, never()).excluirConta(real);
        verify(contaService, times(2)).excluirConta(any());
    }

    @Test
    void criaOAdministradorQuandoNaoExiste() {
        bootstrap(SENHA_FORTE).run();

        ArgumentCaptor<Usuario> salvo = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarios).save(salvo.capture());
        assertEquals(DataSeeder.EMAIL_ADMIN, salvo.getValue().getEmail());
        assertEquals(Set.of(PapelUsuario.ADMIN), salvo.getValue().getPapeis());
        assertTrue(salvo.getValue().getAtivo());
        assertTrue(encoder.matches(SENHA_FORTE, salvo.getValue().getSenha()));
    }

    @Test
    void naoSobeSemSenhaQuandoPrecisaCriarOAdministradorENaoApagaNada() {
        conta(2, DataSeeder.EMAIL_CLIENTE_TESTE, DataSeeder.SENHA_TESTE, PapelUsuario.CLIENTE);
        assertThrows(IllegalStateException.class, () -> bootstrap("").run());
        verify(usuarios, never()).save(any());
        verifyNoInteractions(contaService);
    }

    @Test
    void naoSobeSeOAdministradorFicariaComASenhaPublicaENaoApagaNada() {
        conta(1, DataSeeder.EMAIL_ADMIN, DataSeeder.SENHA_ADMIN, PapelUsuario.ADMIN);
        conta(2, DataSeeder.EMAIL_CLIENTE_TESTE, DataSeeder.SENHA_TESTE, PapelUsuario.CLIENTE);
        assertThrows(IllegalStateException.class, () -> bootstrap("").run());
        verifyNoInteractions(contaService);
        verify(usuarios, never()).save(any());
    }

    @Test
    void trocaASenhaDoAdministradorPelaConfigurada() {
        Usuario admin = conta(1, DataSeeder.EMAIL_ADMIN, DataSeeder.SENHA_ADMIN, PapelUsuario.ADMIN);

        bootstrap(SENHA_FORTE).run();

        verify(usuarios).save(admin);
        assertTrue(encoder.matches(SENHA_FORTE, admin.getSenha()));
        assertFalse(encoder.matches(DataSeeder.SENHA_ADMIN, admin.getSenha()));
    }

    @Test
    void naoRegravaOAdministradorQuandoASenhaJaConfere() {
        conta(1, DataSeeder.EMAIL_ADMIN, SENHA_FORTE, PapelUsuario.ADMIN);
        bootstrap(SENHA_FORTE).run();
        verify(usuarios, never()).save(any());
    }

    @Test
    void mantemOAdministradorComSenhaPropriaQuandoNenhumaFoiConfigurada() {
        conta(1, DataSeeder.EMAIL_ADMIN, "senha-trocada-pelo-admin", PapelUsuario.ADMIN);
        assertDoesNotThrow(() -> bootstrap("").run());
        verify(usuarios, never()).save(any());
    }

    @Test
    void recusaSenhaCurta() {
        assertThrows(IllegalStateException.class, () -> bootstrap("curta").run());
        verifyNoInteractions(contaService);
    }
}
