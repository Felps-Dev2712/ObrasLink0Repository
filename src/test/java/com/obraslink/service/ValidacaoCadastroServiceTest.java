package com.obraslink.service;

import com.obraslink.dto.RegistroUsuarioForm;
import com.obraslink.model.*;
import com.obraslink.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ValidacaoCadastroServiceTest {
    private final ClienteRepository clientes = mock(ClienteRepository.class);
    private final PrestadorRepository prestadores = mock(PrestadorRepository.class);

    @Test
    void clienteInvalidoNaoChegaAoBanco() {
        Cliente cliente = Cliente.builder().cpfCnpj("123").telefone("11999999999").build();
        assertThrows(IllegalArgumentException.class, () -> new ClienteService(clientes).save(cliente));
        verifyNoInteractions(clientes);
    }

    @Test
    void documentoDuplicadoEhComparadoSemMascaraExcluindoProprioId() {
        Cliente cliente = Cliente.builder().id(7L).cpfCnpj("529.982.247-25").build();
        when(clientes.documentoEmUso("52998224725", 7L)).thenReturn(true);
        assertThrows(IllegalArgumentException.class, () -> new ClienteService(clientes).save(cliente));
        verify(clientes, never()).save(any());
    }

    @Test
    void fornecedorSalvaDocumentoWhatsappETelefonesAdicionaisFormatados() {
        Prestador prestador = Prestador.builder().cpfCnpj("12abc34501de35").whatsapp("+55 11 99999-9999").build();
        Telefone telefone = Telefone.builder().numero("2123456789").tipo(TipoTelefone.COMERCIAL).build();
        prestador.getTelefones().add(telefone);
        new PrestadorService(prestadores).save(prestador);
        assertEquals("12.ABC.345/01DE-35", prestador.getCpfCnpj());
        assertEquals("(11) 99999-9999", prestador.getWhatsapp());
        assertEquals("(21) 2345-6789", telefone.getNumero());
        assertSame(prestador, telefone.getPrestador());
    }

    @Test
    void cadastroPublicoValidaAntesDeCriarUsuario() {
        UsuarioRepository usuarios = mock(UsuarioRepository.class);
        AuthService auth = new AuthService(usuarios, clientes, prestadores, mock(CategoriaRepository.class), mock(PasswordEncoder.class));
        RegistroUsuarioForm form = new RegistroUsuarioForm();
        form.setEmail("cliente@exemplo.com");
        form.setSenha("senha1234");
        form.setConfirmarSenha("senha1234");
        form.setCadastroCliente(true);
        form.setClienteNome("Cliente");
        form.setClienteCpfCnpj("11111111111");
        assertThrows(IllegalArgumentException.class, () -> auth.registrar(form));
        verify(usuarios, never()).save(any());
        verify(clientes, never()).save(any());
    }

    @Test
    void cadastroSemPontoNoMapaEhRecusadoAntesDeGravar() {
        UsuarioRepository usuarios = mock(UsuarioRepository.class);
        AuthService auth = new AuthService(usuarios, clientes, prestadores, mock(CategoriaRepository.class), mock(PasswordEncoder.class));
        RegistroUsuarioForm form = new RegistroUsuarioForm();
        form.setEmail("cliente@exemplo.com");
        form.setNome("Cliente Teste");
        form.setSenha("senha1234");
        form.setConfirmarSenha("senha1234");
        form.setCadastroCliente(true);
        form.setClienteNome("Cliente Teste");
        form.setClienteCpfCnpj("529.982.247-25");
        form.setClienteTelefone("(11) 99999-9999");
        form.setClienteCep("01310-100");
        form.setClienteLogradouro("Avenida Paulista");
        form.setClienteNumero("1000");
        form.setClienteBairro("Bela Vista");
        form.setClienteCidade("São Paulo");
        form.setClienteEstado("SP");
        var erro = assertThrows(IllegalArgumentException.class, () -> auth.registrar(form));
        assertTrue(erro.getMessage().contains("mapa"), erro.getMessage());
        verify(usuarios, never()).save(any());
        verify(clientes, never()).save(any());
    }

    private static RegistroUsuarioForm cadastroValidoDeCliente() {
        RegistroUsuarioForm form = new RegistroUsuarioForm();
        form.setEmail("cliente@exemplo.com");
        form.setNome("Cliente Teste");
        form.setSenha("senha1234");
        form.setConfirmarSenha("senha1234");
        form.setCadastroCliente(true);
        form.setClienteCpfCnpj("529.982.247-25");
        form.setClienteTelefone("(11) 99999-9999");
        form.setClienteCep("01310-100");
        form.setClienteLogradouro("Avenida Paulista");
        form.setClienteNumero("1000");
        form.setClienteBairro("Bela Vista");
        form.setClienteCidade("São Paulo");
        form.setClienteEstado("SP");
        form.setClienteLatitude(-23.5614);
        form.setClienteLongitude(-46.6559);
        return form;
    }

    @Test
    void cadastroSemAceitarTermosOuSemCienciaDaPrivacidadeNaoCriaAConta() {
        UsuarioRepository usuarios = mock(UsuarioRepository.class);
        AceiteTermosRepository aceites = mock(AceiteTermosRepository.class);
        AuthService auth = new AuthService(usuarios, clientes, prestadores, mock(CategoriaRepository.class), mock(PasswordEncoder.class));
        org.springframework.test.util.ReflectionTestUtils.setField(auth, "aceiteTermosRepository", aceites);

        RegistroUsuarioForm semTermos = cadastroValidoDeCliente();
        semTermos.setCientePrivacidade(true);
        var erro = assertThrows(IllegalArgumentException.class, () -> auth.registrar(semTermos));
        assertTrue(erro.getMessage().contains("aceite os Termos de Uso"), erro.getMessage());

        RegistroUsuarioForm semPrivacidade = cadastroValidoDeCliente();
        semPrivacidade.setAceitouTermos(true);
        assertThrows(IllegalArgumentException.class, () -> auth.registrar(semPrivacidade));

        verify(usuarios, never()).save(any());
        verify(clientes, never()).save(any());
        verifyNoInteractions(aceites);
    }

    @Test
    void cadastroComAceiteGravaAContaEOAceiteDaVersaoAtual() {
        UsuarioRepository usuarios = mock(UsuarioRepository.class);
        AceiteTermosRepository aceites = mock(AceiteTermosRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(encoder.encode(anyString())).thenReturn("hash");
        when(usuarios.save(any(Usuario.class))).thenAnswer(i -> { Usuario u = i.getArgument(0); u.setId(99L); return u; });
        AuthService auth = new AuthService(usuarios, clientes, prestadores, mock(CategoriaRepository.class), encoder);
        org.springframework.test.util.ReflectionTestUtils.setField(auth, "aceiteTermosRepository", aceites);

        RegistroUsuarioForm form = cadastroValidoDeCliente();
        form.setAceitouTermos(true);
        form.setCientePrivacidade(true);
        auth.registrar(form);

        verify(usuarios).save(any(Usuario.class));
        verify(aceites).save(argThat(a -> a.getUsuarioId().equals(99L) && a.getVersao().equals(TermosService.VERSAO_ATUAL) && a.getAceitoEm() != null));
        verify(clientes).save(any());
    }
}
