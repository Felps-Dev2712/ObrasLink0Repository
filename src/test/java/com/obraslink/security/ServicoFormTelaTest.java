package com.obraslink.security;

import com.obraslink.model.*;
import com.obraslink.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Cadastro e edição de serviço pelo prestador: preço em reais e categoria fixa (especialidade principal). */
@SpringJUnitConfig({TermosSecurityTest.Config.class, ServicoDetalheTelaTest.Config.class})
@WebAppConfiguration
class ServicoFormTelaTest {
    @Autowired WebApplicationContext context;
    @Autowired AcessoService acesso;
    @Autowired PrestadorService prestadores;
    @Autowired ServicoService servicos;
    @Autowired CategoriaService categorias;
    private MockMvc mvc;
    private final Categoria eletricista = Categoria.builder().id(1L).nome("Eletricista").build();
    private Prestador prestador;

    @BeforeEach
    void preparar() {
        reset(acesso, prestadores, servicos, categorias);
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        Usuario usuario = Usuario.builder().id(40L).ativo(true).papeis(Set.of(PapelUsuario.PRESTADOR)).build();
        prestador = Prestador.builder().id(30L).usuarioId(40L).nome("Camille Profissional").categoria(eletricista).build();
        when(acesso.getUsuarioAtualOrThrow()).thenReturn(usuario);
        when(acesso.getUsuarioAtual()).thenReturn(Optional.of(usuario));
        when(acesso.hasPapel(PapelUsuario.PRESTADOR)).thenReturn(true);
        when(prestadores.findByUsuarioId(40L)).thenReturn(Optional.of(prestador));
        when(prestadores.findById(30L)).thenReturn(Optional.of(prestador));
        when(categorias.findAll()).thenReturn(List.of(eletricista, Categoria.builder().id(5L).nome("Mestre de Obras").build()));
        when(servicos.save(any(Servico.class))).thenAnswer(i -> { Servico s = i.getArgument(0); if (s.getId() == null) s.setId(77L); return s; });
    }

    private Servico salvo() {
        var captor = ArgumentCaptor.forClass(Servico.class);
        verify(servicos).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void formularioMostraCategoriaFixaDoPrestadorEPrecoComMascara() throws Exception {
        String html = mvc.perform(get("/servicos/novo")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(html.contains("value=\"Eletricista\"") && html.contains("readonly"), "categoria fixa e preenchida");
        assertFalse(html.contains("name=\"categoria\""), "categoria não é enviada pelo formulário");
        assertFalse(html.contains("Mestre de Obras"), "sem lista de categorias para escolher");
        assertTrue(html.contains("data-mask=\"moeda\"") && html.contains("name=\"preco\""));
    }

    @Test
    void salvaPrecoFormatadoComPrecisaoEIgnoraCategoriaEnviadaPorFora() throws Exception {
        mvc.perform(post("/servicos").param("titulo", "Troca de quadro").param("descricao", "Desc")
                        .param("preco", "R$ 2.000,50").param("categoria", "5").param("disponivel", "true"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/servicos"));
        Servico s = salvo();
        assertEquals(new BigDecimal("2000.50"), s.getPreco());
        assertEquals(eletricista, s.getCategoria(), "categoria = especialidade principal, não a enviada");
        assertSame(prestador, s.getPrestador());
    }

    @Test
    void precoEmBrancoFicaSobConsulta() throws Exception {
        mvc.perform(post("/servicos").param("titulo", "Visita").param("descricao", "Desc").param("preco", ""))
                .andExpect(redirectedUrl("/servicos"));
        assertNull(salvo().getPreco());
    }

    @Test
    void precoInvalidoVoltaParaOFormularioComMensagemSemSalvar() throws Exception {
        mvc.perform(post("/servicos").param("titulo", "Visita").param("descricao", "Desc").param("preco", "10,123"))
                .andExpect(redirectedUrl("/servicos/novo"))
                .andExpect(flash().attribute("mensagemErro", com.obraslink.validation.Moeda.MENSAGEM_INVALIDO));
        verify(servicos, never()).save(any());
    }

    @Test
    void prestadorSemEspecialidadePrincipalNaoSalvaServico() throws Exception {
        prestador.setCategoria(null);
        mvc.perform(post("/servicos").param("titulo", "Visita").param("descricao", "Desc").param("categoria", "5"))
                .andExpect(redirectedUrl("/servicos/novo")).andExpect(flash().attributeExists("mensagemErro"));
        verify(servicos, never()).save(any());
    }

    @Test
    void edicaoMostraPrecoFormatadoEReaplicaACategoriaDoPrestador() throws Exception {
        Servico existente = Servico.builder().id(9L).titulo("Antigo").descricao("Desc").preco(new BigDecimal("58.66"))
                .categoria(Categoria.builder().id(5L).nome("Mestre de Obras").build()).prestador(prestador).disponivel(true).build();
        when(acesso.podeGerenciarServico(9L)).thenReturn(true);
        when(servicos.findById(9L)).thenReturn(Optional.of(existente));

        String html = mvc.perform(get("/servicos/9/editar")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(html.contains("value=\"R$ 58,66\""), "preço salvo aparece formatado ao editar");

        mvc.perform(post("/servicos/9").param("titulo", "Antigo").param("descricao", "Desc").param("preco", "R$ 1.500,00").param("categoria", "5"))
                .andExpect(redirectedUrl("/servicos"));
        Servico s = salvo();
        assertEquals(new BigDecimal("1500.00"), s.getPreco());
        assertEquals(eletricista, s.getCategoria());
    }
}
