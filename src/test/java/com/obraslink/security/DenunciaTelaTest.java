package com.obraslink.security;

import com.obraslink.controller.DenunciaController;
import com.obraslink.model.Atendimento;
import com.obraslink.model.Denuncia;
import com.obraslink.service.DenunciaService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Tela de denúncia de um atendimento (ver {@link DenunciaController}). */
@SpringJUnitConfig({TermosSecurityTest.Config.class, DenunciaTelaTest.Config.class})
@WebAppConfiguration
class DenunciaTelaTest {
    @Configuration
    @Import(DenunciaController.class)
    static class Config {
        @Bean DenunciaService denunciaService() { return mock(DenunciaService.class); }
    }

    @Autowired WebApplicationContext context;
    @Autowired DenunciaService service;
    MockMvc mvc;
    Atendimento atendimento;

    @BeforeEach
    void preparar() {
        reset(service);
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        atendimento = new Atendimento();
        atendimento.setId(50L);
        atendimento.setClienteNome("Camila");
        atendimento.setPrestadorNome("Rafael");
        atendimento.setCriadoEm(LocalDateTime.of(2026, 11, 10, 9, 0));
    }

    @Test
    void mostraOFormularioComOsMotivosQuandoAindaPodeDenunciar() throws Exception {
        when(service.consultar(50L)).thenReturn(new DenunciaService.Detalhes(atendimento, null, true));
        String html = mvc.perform(get("/atendimentos/50/denunciar")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertTrueContains(html, "Denunciar profissional");
        for (var motivo : Denuncia.Motivo.values()) assertTrueContains(html, motivo.getRotulo());
        assertTrueContains(html, "type=\"submit\"");
        assertFalseContains(html, "#50");
    }

    @Test
    void mostraADenunciaJaEnviadaSemFormularioDeNovo() throws Exception {
        Denuncia denuncia = Denuncia.builder().motivo(Denuncia.Motivo.FRAUDE).descricao("<script>alert(1)</script>")
                .estado(Denuncia.Estado.PENDENTE).criadoEm(LocalDateTime.of(2026, 11, 11, 10, 0)).build();
        when(service.consultar(50L)).thenReturn(new DenunciaService.Detalhes(atendimento, denuncia, false));
        String html = mvc.perform(get("/atendimentos/50/denunciar")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertFalseContains(html, "type=\"submit\" class=\"btn btn-danger\"");
        assertTrueContains(html, "Em análise");
        assertFalseContains(html, "<script>alert(1)</script>");
    }

    @Test
    void semAcessoVoltaParaNotificacoesComAMensagem() throws Exception {
        when(service.consultar(50L)).thenThrow(new IllegalArgumentException("Você não tem acesso a este atendimento."));
        mvc.perform(get("/atendimentos/50/denunciar")).andExpect(redirectedUrl("/notificacoes"))
                .andExpect(flash().attribute("mensagemErro", "Você não tem acesso a este atendimento."));
    }

    @Test
    void enviaADenunciaERedirecionaParaNotificacoesComMensagem() throws Exception {
        mvc.perform(post("/atendimentos/50/denunciar").param("motivo", "COMPORTAMENTO_INADEQUADO").param("descricao", "Foi grosseiro."))
                .andExpect(redirectedUrl("/notificacoes")).andExpect(flash().attribute("mensagem", "Denúncia registrada. Nossa equipe vai analisar."));
        verify(service).denunciar(eq(50L), any());
    }

    @Test
    void erroDoServicoVoltaParaOFormularioPreservandoOTexto() throws Exception {
        doThrow(new IllegalArgumentException("Você já denunciou este atendimento.")).when(service).denunciar(eq(50L), any());
        mvc.perform(post("/atendimentos/50/denunciar").param("motivo", "OUTRO").param("descricao", "Algo"))
                .andExpect(redirectedUrl("/atendimentos/50/denunciar"))
                .andExpect(flash().attribute("mensagemErro", "Você já denunciou este atendimento."))
                .andExpect(flash().attributeExists("denunciaForm"));
    }

    private static void assertTrueContains(String html, String trecho) {
        org.junit.jupiter.api.Assertions.assertTrue(html.contains(trecho), () -> "esperava conter: " + trecho);
    }

    private static void assertFalseContains(String html, String trecho) {
        org.junit.jupiter.api.Assertions.assertFalse(html.contains(trecho), () -> "não devia conter: " + trecho);
    }
}
