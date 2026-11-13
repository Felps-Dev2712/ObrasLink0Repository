package com.obraslink.security;

import com.obraslink.controller.AdminDenunciaController;
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
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Tela de administração das denúncias pendentes (ver {@link AdminDenunciaController}). */
@SpringJUnitConfig({TermosSecurityTest.Config.class, AdminDenunciaTelaTest.Config.class})
@WebAppConfiguration
class AdminDenunciaTelaTest {
    @Configuration
    @Import(AdminDenunciaController.class)
    static class Config {
        @Bean DenunciaService denunciaService() { return mock(DenunciaService.class); }
    }

    @Autowired WebApplicationContext context;
    @Autowired DenunciaService service;
    MockMvc mvc;

    @BeforeEach
    void preparar() {
        reset(service);
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void listaAsDenunciasPendentesComOMotivoEADescricao() throws Exception {
        var d = Denuncia.builder().id(7L).atendimentoId(50L).autorNome("Camila").denunciadoNome("Rafael")
                .motivo(Denuncia.Motivo.NAO_COMPARECIMENTO).descricao("Não apareceu no horário combinado.")
                .estado(Denuncia.Estado.PENDENTE).criadoEm(LocalDateTime.of(2026, 11, 10, 9, 0)).build();
        when(service.pendentes()).thenReturn(List.of(d));
        String html = mvc.perform(get("/admin/denuncias")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertTrue(html.contains("Camila"));
        org.junit.jupiter.api.Assertions.assertTrue(html.contains("Rafael"));
        org.junit.jupiter.api.Assertions.assertTrue(html.contains("Não apareceu no horário combinado."));
        org.junit.jupiter.api.Assertions.assertTrue(html.contains("value=\"PROCEDENTE\""));
        org.junit.jupiter.api.Assertions.assertTrue(html.contains("value=\"IMPROCEDENTE\""));
    }

    @Test
    void semDenunciasMostraEstadoVazio() throws Exception {
        when(service.pendentes()).thenReturn(List.of());
        String html = mvc.perform(get("/admin/denuncias")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertTrue(html.contains("Tudo em dia"));
    }

    @Test
    void analisarRedirecionaComMensagemDeSucesso() throws Exception {
        mvc.perform(post("/admin/denuncias/7/analise").param("estado", "PROCEDENTE").param("observacao", "Confirmado"))
                .andExpect(redirectedUrl("/admin/denuncias")).andExpect(flash().attribute("mensagem", "Denúncia analisada."));
        verify(service).analisar(7L, Denuncia.Estado.PROCEDENTE, "Confirmado");
    }

    @Test
    void erroDoServicoVoltaComMensagemDeErro() throws Exception {
        doThrow(new IllegalArgumentException("Apenas administradores ativos podem analisar denúncias."))
                .when(service).analisar(eq(7L), any(), any());
        mvc.perform(post("/admin/denuncias/7/analise").param("estado", "PROCEDENTE"))
                .andExpect(redirectedUrl("/admin/denuncias"))
                .andExpect(flash().attribute("mensagemErro", "Apenas administradores ativos podem analisar denúncias."));
    }
}
