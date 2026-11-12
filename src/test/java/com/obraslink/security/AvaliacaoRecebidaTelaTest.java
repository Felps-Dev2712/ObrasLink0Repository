package com.obraslink.security;

import com.obraslink.controller.AvaliacaoRecebidaController;
import com.obraslink.model.*;
import com.obraslink.service.AvaliacaoPrestadorService;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Tela da avaliação que o prestador RECEBEU do cliente (/atendimentos/{id}/avaliacao-profissional). */
@SpringJUnitConfig({TermosSecurityTest.Config.class, AvaliacaoRecebidaTelaTest.Config.class})
@WebAppConfiguration
class AvaliacaoRecebidaTelaTest {
    @Configuration
    @Import(AvaliacaoRecebidaController.class)
    static class Config {
        @Bean AvaliacaoPrestadorService avaliacaoPrestadorService() { return mock(AvaliacaoPrestadorService.class); }
    }

    @Autowired WebApplicationContext context;
    @Autowired AvaliacaoPrestadorService service;
    private MockMvc mvc;
    private Atendimento atendimento;

    @BeforeEach
    void preparar() {
        reset(service);
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        atendimento = new Atendimento();
        atendimento.setId(31L);
        atendimento.setClienteNome("Camille Cliente");
        atendimento.setPrestadorNome("Camille Profissional");
        atendimento.setServicoTitulo("Troca de quadro");
        atendimento.setConcluidoEm(LocalDateTime.of(2026, 11, 12, 1, 0));
    }

    @Test
    void mostraOsCincoCriteriosDoClienteMediaComentarioEQuemAvaliou() throws Exception {
        AvaliacaoPrestador avaliacao = AvaliacaoPrestador.builder().atendimentoId(31L)
                .cliente(Cliente.builder().nome("Camille Cliente").build())
                .qualidade(5).prazo(4).preco(5).organizacao(4).atendimento(5).comentario("Ótimo trabalho")
                .criadoEm(LocalDateTime.of(2026, 11, 12, 2, 0)).build();
        when(service.consultarRecebida(31L)).thenReturn(new AvaliacaoPrestadorService.Recebida(atendimento, avaliacao, true));

        String html = org.springframework.web.util.HtmlUtils.htmlUnescape(mvc.perform(get("/atendimentos/31/avaliacao-profissional"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertTrue(html.contains("Avaliação recebida"));
        for (String criterio : new String[]{"Qualidade", "Prazo", "Preço", "Organização", "Atendimento"}) assertTrue(html.contains(criterio), criterio);
        assertFalse(html.contains("Comunicação") || html.contains("Cumprimento do combinado"), "não são os critérios da avaliação do cliente");
        assertTrue(html.contains("★ 4.6 média"));
        assertTrue(html.contains("Ótimo trabalho"));
        assertTrue(html.contains("Avaliado por") && html.contains("Camille Cliente"));
        assertFalse(html.contains("Atendimento #"));
    }

    @Test
    void semAvaliacaoAindaMostraAvisoEAcessoNegadoVoltaParaAtendimentos() throws Exception {
        when(service.consultarRecebida(31L)).thenReturn(new AvaliacaoPrestadorService.Recebida(atendimento, null, true));
        String html = mvc.perform(get("/atendimentos/31/avaliacao-profissional")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(org.springframework.web.util.HtmlUtils.htmlUnescape(html).contains("O cliente ainda não avaliou este atendimento."));

        when(service.consultarRecebida(32L)).thenThrow(new IllegalArgumentException("Você não tem acesso a esta avaliação."));
        mvc.perform(get("/atendimentos/32/avaliacao-profissional")).andExpect(redirectedUrl("/notificacoes"))
                .andExpect(flash().attribute("mensagemErro", "Você não tem acesso a esta avaliação."));
    }
}
