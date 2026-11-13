package com.obraslink.security;

import com.obraslink.model.Categoria;
import com.obraslink.model.Prestador;
import com.obraslink.model.Servico;
import com.obraslink.service.CategoriaService;
import com.obraslink.service.ServicoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Busca livre da vitrine (campo de texto na home, em vez do antigo select de categoria): filtra por título,
 * descrição, categoria ou nome do profissional, ignorando maiúsculas/minúsculas e espaços nas pontas.
 */
@SpringJUnitConfig({TermosSecurityTest.Config.class, ServicoDetalheTelaTest.Config.class})
@WebAppConfiguration
class VitrineBuscaTelaTest {
    @Autowired WebApplicationContext context;
    @Autowired ServicoService servicos;
    @Autowired CategoriaService categorias;
    private MockMvc mvc;

    private Servico servico(String titulo, String descricao, Categoria categoria, Prestador prestador) {
        return Servico.builder().id(1L).titulo(titulo).descricao(descricao).preco(new BigDecimal("100.00"))
                .categoria(categoria).prestador(prestador).disponivel(true).build();
    }

    @BeforeEach
    void preparar() {
        reset(servicos, categorias);
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        when(categorias.findAll()).thenReturn(List.of());
    }

    @Test
    void semTermoMostraTodosOsServicosDaCategoria() throws Exception {
        var eletricista = Categoria.builder().id(1L).nome("Eletricista").build();
        var prestador = Prestador.builder().id(5L).nome("João Silva").build();
        var s = servico("Troca de quadro", "Desc", eletricista, prestador);
        when(servicos.findByCategoria(null)).thenReturn(List.of(s));

        String html = mvc.perform(get("/vitrine")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(html.contains("Troca de quadro"));
        assertFalse(html.contains("Resultados para"));
    }

    @Test
    void filtraPorTituloDescricaoCategoriaOuNomeDoProfissionalIgnorandoCaixa() throws Exception {
        var eletricista = Categoria.builder().id(1L).nome("Eletricista").build();
        var pintor = Categoria.builder().id(2L).nome("Pintor").build();
        var joao = Prestador.builder().id(5L).nome("João Silva").build();
        var maria = Prestador.builder().id(6L).nome("Maria Pintora").build();
        var troca = servico("Troca de quadro", "Substituição do quadro antigo", eletricista, joao);
        var pintura = servico("Pintura de muro", "Pintura externa completa", pintor, maria);
        when(servicos.findByCategoria(null)).thenReturn(List.of(troca, pintura));

        // por título/descrição
        String html = mvc.perform(get("/vitrine").param("q", "PINTURA")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(html.contains("Pintura de muro"));
        assertFalse(html.contains("Troca de quadro"));

        // por nome do profissional (não pelo título do serviço)
        html = mvc.perform(get("/vitrine").param("q", "joão")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(html.contains("Troca de quadro"));
        assertFalse(html.contains("Pintura de muro"));
    }

    @Test
    void ignoraEspacosNasPontasDoTermo() throws Exception {
        var eletricista = Categoria.builder().id(1L).nome("Eletricista").build();
        var joao = Prestador.builder().id(5L).nome("João Silva").build();
        var s = servico("Troca de quadro", "Desc", eletricista, joao);
        when(servicos.findByCategoria(null)).thenReturn(List.of(s));

        String html = mvc.perform(get("/vitrine").param("q", "  quadro  ")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(html.contains("Troca de quadro"));
    }

    @Test
    void campoVazioOuSoComEspacosNaoFiltraNada() throws Exception {
        var eletricista = Categoria.builder().id(1L).nome("Eletricista").build();
        var joao = Prestador.builder().id(5L).nome("João Silva").build();
        var s = servico("Troca de quadro", "Desc", eletricista, joao);
        when(servicos.findByCategoria(null)).thenReturn(List.of(s));

        String html = mvc.perform(get("/vitrine").param("q", "   ")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(html.contains("Troca de quadro"));
        assertFalse(html.contains("Resultados para"), "espaços em branco contam como busca vazia: sem filtro, sem mensagem de resultado");
    }

    @Test
    void semResultadoMostraMensagemComOTermoEOpcaoDeLimparABusca() throws Exception {
        when(servicos.findByCategoria(null)).thenReturn(List.of());

        String html = mvc.perform(get("/vitrine").param("q", "encanador hidráulico"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertTrue(html.contains("Nenhum resultado para"));
        assertTrue(html.contains("encanador hidráulico"));
        assertTrue(html.contains("Limpar busca"));
        assertTrue(html.contains("href=\"/vitrine\""));
    }
}
