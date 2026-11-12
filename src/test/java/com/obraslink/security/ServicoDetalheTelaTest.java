package com.obraslink.security;

import com.obraslink.controller.ServicoController;
import com.obraslink.model.*;
import com.obraslink.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Página de detalhes do serviço (/servicos/{id}). */
@SpringJUnitConfig({TermosSecurityTest.Config.class, ServicoDetalheTelaTest.Config.class})
@WebAppConfiguration
class ServicoDetalheTelaTest {
    @Configuration
    @Import(ServicoController.class)
    static class Config {
        @Bean ServicoService servicoService() { return mock(ServicoService.class); }
        @Bean PrestadorService prestadorService() { return mock(PrestadorService.class); }
        @Bean CategoriaService categoriaService() { return mock(CategoriaService.class); }
        @Bean RelacionamentosService relacionamentosService() { return mock(RelacionamentosService.class); }
        @Bean ServicoDetalheService servicoDetalheService() { return mock(ServicoDetalheService.class); }
    }

    @Autowired WebApplicationContext context;
    @Autowired ServicoDetalheService detalhes;
    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        reset(detalhes);
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void mostraNomeDescricaoCompletaTodasAsFotosPrecoCategoriaDisponibilidadeEAvaliacoes() throws Exception {
        Categoria pintor = Categoria.builder().id(2L).nome("Pintor").icone("🎨").build();
        Prestador prestador = Prestador.builder().id(30L).nome("Camille Profissional").usuarioId(40L).build();
        String descricao = "Descrição longa. ".repeat(80).trim();
        Servico servico = Servico.builder().id(5L).titulo("Pintura de fachada").descricao(descricao)
                .preco(new BigDecimal("58.66")).categoria(pintor).prestador(prestador).disponivel(true).build();
        Cliente cliente = Cliente.builder().id(20L).nome("Camila").build();
        AvaliacaoPrestador avaliacao = AvaliacaoPrestador.builder().id(9L).cliente(cliente).qualidade(5).prazo(4).preco(5)
                .organizacao(4).atendimento(5).comentario("Ficou ótimo").criadoEm(LocalDateTime.of(2026, 11, 10, 9, 0)).build();
        var fotos = List.of(new ServicoFoto(5L, "a1.jpg"), new ServicoFoto(5L, "a2.jpg"), new ServicoFoto(5L, "a3.jpg"),
                new ServicoFoto(5L, "a4.jpg"), new ServicoFoto(5L, "a5.jpg"), new ServicoFoto(5L, "a6.jpg"));
        when(detalhes.carregar(5L)).thenReturn(Optional.of(new ServicoDetalheService.Detalhe(servico, fotos, List.of(avaliacao),
                new BigDecimal("4.6"), 3, new BigDecimal("4.2"))));

        String html = mvc.perform(get("/servicos/5")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String texto = org.springframework.web.util.HtmlUtils.htmlUnescape(html);
        assertTrue(texto.contains("Pintura de fachada"));
        assertTrue(texto.contains(descricao), "descrição completa, sem corte");
        for (int i = 1; i <= 6; i++) assertTrue(html.contains("/imagens/a" + i + ".jpg"), "foto " + i);
        assertEquals(6, html.split("data-lightbox-group=\"servico-detalhe\"", -1).length - 1, "todas as fotos ampliáveis");
        assertTrue(texto.contains("A partir de R$ 58,66"));
        assertTrue(texto.contains("🎨 Pintor"));
        assertTrue(texto.contains("Disponível para contratação"));
        assertTrue(texto.contains("Avaliações deste serviço") && texto.contains("Ficou ótimo") && texto.contains("★ 4.6 média"));
        assertTrue(texto.contains("Camille Profissional") && html.contains("href=\"/prestadores/30\""), "identifica o prestador e leva ao perfil");
    }

    @Test
    void servicoInexistenteVoltaParaAVitrineComAviso() throws Exception {
        when(detalhes.carregar(99L)).thenReturn(Optional.empty());
        mvc.perform(get("/servicos/99")).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/vitrine")).andExpect(flash().attributeExists("mensagemErro"));
    }
}
