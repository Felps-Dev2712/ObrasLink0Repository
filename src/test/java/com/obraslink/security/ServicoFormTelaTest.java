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

/**
 * Cadastro e edição de serviço: preço em reais e categoria escolhida entre as especialidades do PRÓPRIO
 * prestador (principal + secundárias) — nunca uma categoria de fora, mesmo que alguém a envie por fora da tela.
 */
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
    private final Categoria pintor = Categoria.builder().id(2L).nome("Pintor").build();
    private final Categoria mestreDeObras = Categoria.builder().id(5L).nome("Mestre de Obras").build(); // não é do prestador
    private Prestador prestador;

    @BeforeEach
    void preparar() {
        reset(acesso, prestadores, servicos, categorias);
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        Usuario usuario = Usuario.builder().id(40L).ativo(true).papeis(Set.of(PapelUsuario.PRESTADOR)).build();
        prestador = Prestador.builder().id(30L).usuarioId(40L).nome("Camille Profissional").categoria(eletricista).build();
        prestador.getCategorias().add(eletricista); // PrestadorService.save() garante a principal na lista; aqui simulamos à mão
        when(acesso.getUsuarioAtualOrThrow()).thenReturn(usuario);
        when(acesso.getUsuarioAtual()).thenReturn(Optional.of(usuario));
        when(acesso.hasPapel(PapelUsuario.PRESTADOR)).thenReturn(true);
        when(prestadores.findByUsuarioId(40L)).thenReturn(Optional.of(prestador));
        when(prestadores.findById(30L)).thenReturn(Optional.of(prestador));
        when(categorias.findAll()).thenReturn(List.of(eletricista, pintor, mestreDeObras));
        when(servicos.save(any(Servico.class))).thenAnswer(i -> { Servico s = i.getArgument(0); if (s.getId() == null) s.setId(77L); return s; });
    }

    private Servico salvo() {
        var captor = ArgumentCaptor.forClass(Servico.class);
        verify(servicos).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void comUmaSoEspecialidadeOFormularioJaMostraEEnviaEla() throws Exception {
        String html = mvc.perform(get("/servicos/novo")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(html.contains("name=\"categoria\""), "categoria é enviada pelo formulário");
        assertTrue(html.contains("<select") && html.contains("id=\"categoria\""), "é um select, não um campo travado");
        assertTrue(html.contains(">Eletricista<"));
        assertFalse(html.contains(">Mestre de Obras<"), "só as especialidades do prestador, não todas as categorias do site");
        assertTrue(html.contains("data-mask=\"moeda\"") && html.contains("name=\"preco\""));
    }

    @Test
    void comDuasEspecialidadesOPrestadorEscolheEntreAsDuasAoCriarOServico() throws Exception {
        prestador.getCategorias().add(pintor);
        String html = mvc.perform(get("/servicos/novo")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(html.contains(">Eletricista<") && html.contains(">Pintor<"), "as duas especialidades aparecem no select");

        mvc.perform(post("/servicos").param("titulo", "Pintura externa").param("descricao", "Desc")
                        .param("preco", "R$ 2.000,50").param("categoria.id", "2").param("disponivel", "true"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/servicos"));
        Servico s = salvo();
        assertEquals(new BigDecimal("2000.50"), s.getPreco());
        assertEquals(pintor, s.getCategoria(), "salva a especialidade escolhida, não obrigatoriamente a principal");
        assertSame(prestador, s.getPrestador());
    }

    @Test
    void categoriaQueNaoEDoPrestadorNuncaEhAceitaEVoltaParaAPrincipal() throws Exception {
        prestador.getCategorias().add(pintor); // ele tem Eletricista e Pintor, mas não Mestre de Obras
        mvc.perform(post("/servicos").param("titulo", "Serviço qualquer").param("descricao", "Desc").param("categoria.id", "5"))
                .andExpect(redirectedUrl("/servicos"));
        assertEquals(eletricista, salvo().getCategoria(), "categoria 5 não é do prestador: cai para a especialidade principal");
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
    void prestadorSemNenhumaEspecialidadeNaoSalvaServico() throws Exception {
        prestador.setCategoria(null);
        prestador.getCategorias().clear();
        mvc.perform(post("/servicos").param("titulo", "Visita").param("descricao", "Desc").param("categoria.id", "5"))
                .andExpect(redirectedUrl("/servicos/novo")).andExpect(flash().attributeExists("mensagemErro"));
        verify(servicos, never()).save(any());
    }

    @Test
    void edicaoMostraOSelectComAsEspecialidadesEReaplicaARegraAoSalvar() throws Exception {
        prestador.getCategorias().add(pintor);
        Servico existente = Servico.builder().id(9L).titulo("Antigo").descricao("Desc").preco(new BigDecimal("58.66"))
                .categoria(pintor).prestador(prestador).disponivel(true).build();
        when(acesso.podeGerenciarServico(9L)).thenReturn(true);
        when(servicos.findById(9L)).thenReturn(Optional.of(existente));

        String html = mvc.perform(get("/servicos/9/editar")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(html.contains("value=\"R$ 58,66\""), "preço salvo aparece formatado ao editar");
        assertTrue(html.contains(">Eletricista<") && html.contains(">Pintor<"));

        // Tenta enviar uma categoria que não é do prestador: a edição não grava essa categoria.
        mvc.perform(post("/servicos/9").param("titulo", "Antigo").param("descricao", "Desc").param("preco", "R$ 1.500,00").param("categoria.id", "5"))
                .andExpect(redirectedUrl("/servicos"));
        Servico s = salvo();
        assertEquals(new BigDecimal("1500.00"), s.getPreco());
        assertEquals(eletricista, s.getCategoria(), "categoria 5 não é do prestador: cai para a principal");
    }

    @Test
    void admContinuaUsandoAEspecialidadePrincipalDoProfissionalEscolhidoIgnorandoOCampoEnviado() throws Exception {
        when(acesso.isAdmin()).thenReturn(true);
        when(prestadores.findAllActive()).thenReturn(List.of(prestador));
        mvc.perform(post("/servicos").param("titulo", "Serviço via admin").param("descricao", "Desc")
                        .param("prestador.id", "30").param("categoria.id", "2")) // admin tenta enviar Pintor
                .andExpect(redirectedUrl("/servicos"));
        assertEquals(eletricista, salvo().getCategoria(), "admin continua com a especialidade principal, sem select próprio");
    }
}
