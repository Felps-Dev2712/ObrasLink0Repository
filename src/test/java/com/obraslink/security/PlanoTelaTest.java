package com.obraslink.security;

import com.obraslink.controller.PlanoController;
import com.obraslink.model.Plano;
import com.obraslink.service.PlanoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Tela de planos (/prestador/planos): comparação dos planos e troca (simulada) do plano atual. */
@SpringJUnitConfig({TermosSecurityTest.Config.class, PlanoTelaTest.Config.class})
@WebAppConfiguration
class PlanoTelaTest {
    @Configuration
    @Import(PlanoController.class)
    static class Config {
        @Bean PlanoService planoService() { return mock(PlanoService.class); }
    }

    @Autowired WebApplicationContext context;
    @Autowired PlanoService planoService;
    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        reset(planoService);
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        when(planoService.todos()).thenReturn(java.util.List.of(Plano.values()));
    }

    @Test
    void mostraOsQuatroPlanosEDestacaOPlanoAtual() throws Exception {
        var prestador = com.obraslink.model.Prestador.builder().id(30L).nome("Fornecedor").plano(Plano.ESSENCIAL).build();
        when(planoService.prestadorAtual()).thenReturn(prestador);

        String html = mvc.perform(get("/prestador/planos")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        for (Plano plano : Plano.values()) assertTrue(html.contains(plano.getNome()), plano.getNome());
        assertTrue(html.contains("Seu plano atual"));
    }

    @Test
    void semPerfilDePrestadorAtivoVoltaParaMinhaContaComAviso() throws Exception {
        when(planoService.prestadorAtual()).thenThrow(new IllegalArgumentException("Ative o perfil de prestador para gerenciar seu plano."));

        mvc.perform(get("/prestador/planos"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/clientes"))
                .andExpect(flash().attribute("mensagemErro", "Ative o perfil de prestador para gerenciar seu plano."));
    }

    @Test
    void assinarTrocaOPlanoEVoltaComMensagemDeSucesso() throws Exception {
        when(planoService.assinar(Plano.PROFISSIONAL)).thenReturn(Plano.PROFISSIONAL);

        mvc.perform(post("/prestador/planos/assinar").param("plano", "PROFISSIONAL"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/prestador/planos"))
                .andExpect(flash().attribute("mensagem", "Plano Profissional ativado com sucesso!"));
        verify(planoService).assinar(Plano.PROFISSIONAL);
    }

    @Test
    void assinarMostraOErroQuandoOServicoRecusa() throws Exception {
        when(planoService.assinar(Plano.PROFISSIONAL)).thenThrow(new IllegalArgumentException("Ative o perfil de prestador para gerenciar seu plano."));

        mvc.perform(post("/prestador/planos/assinar").param("plano", "PROFISSIONAL"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("mensagemErro", "Ative o perfil de prestador para gerenciar seu plano."));
    }
}
