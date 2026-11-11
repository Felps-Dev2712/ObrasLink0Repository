package com.obraslink.security;

import com.obraslink.controller.PrestadorController;
import com.obraslink.dto.AvaliacaoPrestadorForm;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitConfig({TermosSecurityTest.Config.class, AvaliacaoTelaTest.Config.class})
@WebAppConfiguration
class AvaliacaoTelaTest {
    @Configuration
    @Import(PrestadorController.class)
    static class Config {
        @Bean PrestadorService prestadorService() { return mock(PrestadorService.class); }
        @Bean CategoriaService categoriaService() { return mock(CategoriaService.class); }
        @Bean AvaliacaoPrestadorService avaliacaoService() { return mock(AvaliacaoPrestadorService.class); }
    }
    @Autowired WebApplicationContext context;
    @Autowired PrestadorService prestadores;
    @Autowired AvaliacaoPrestadorService avaliacoes;
    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        reset(prestadores, avaliacoes);
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        when(prestadores.findPerfilPublico(30L)).thenReturn(Optional.of(Prestador.builder().id(30L).nome("Fornecedor").build()));
        Atendimento contratado = new Atendimento();
        contratado.setId(50L);
        contratado.setConcluidoEm(LocalDateTime.of(2026, 9, 10, 12, 0));
        when(avaliacoes.atendimentosDisponiveis(30L)).thenReturn(List.of(contratado));
    }

    @Test
    void renderizaCincoGruposIndependentesSemNotasAutomaticasEBotaoEnviar() throws Exception {
        String html = mvc.perform(get("/prestadores/30")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        for (String criterio : List.of("qualidade", "prazo", "preco", "organizacao", "atendimento")) {
            var campos = Pattern.compile("<input[^>]*name=\"" + criterio + "\"[^>]*>").matcher(html).results().toList();
            assertEquals(5, campos.size(), criterio);
            campos.forEach(campo -> {
                assertTrue(campo.group().contains("type=\"radio\""));
                assertTrue(campo.group().contains("required"));
                assertFalse(campo.group().contains("checked="));
            });
        }
        assertTrue(html.contains("Enviar avaliação"));
        assertTrue(html.contains("Atendimento #50"));
        assertFalse(html.contains("name=\"atendimentoId\"")); assertTrue(html.contains("/atendimentos/50/avaliacoes"));
        assertFalse(html.contains("requestSubmit"));
    }

    @Test
    void semAtendimentoConcluidoNaoExibeFormulario() throws Exception {
        when(avaliacoes.atendimentosDisponiveis(30L)).thenReturn(List.of());
        String html = mvc.perform(get("/prestadores/30")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertFalse(html.contains("data-rating-form"));
        assertTrue(html.contains("Cada atendimento pode ser avaliado uma vez"));
    }

    @Test
    void falhaDeValidacaoPreservaNotasEComentario() throws Exception {
        doThrow(new IllegalArgumentException("Selecione todas as notas.")).when(avaliacoes).avaliar(eq(30L),eq(50L),any());
        var result = mvc.perform(post("/prestadores/30/atendimentos/50/avaliacoes")
                        .param("qualidade", "5").param("preco", "2").param("comentario", "Minha experiência"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/prestadores/30#avaliacoes"))
                .andExpect(flash().attributeExists("mensagemErro", "avaliacaoForm")).andReturn();
        var form = (AvaliacaoPrestadorForm) result.getFlashMap().get("avaliacaoForm");
        assertEquals(5, form.getQualidade());
        assertEquals(2, form.getPreco());
        assertNull(form.getPrazo());
        assertEquals("Minha experiência", form.getComentario());
        String html = mvc.perform(get("/prestadores/30").flashAttrs(result.getFlashMap()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(org.springframework.web.util.HtmlUtils.htmlUnescape(html).contains("Minha experiência"));
        assertEquals(2, Pattern.compile("checked=\"checked\"").matcher(html).results().count());
    }
}
