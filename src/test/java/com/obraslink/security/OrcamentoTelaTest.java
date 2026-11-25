package com.obraslink.security;

import com.obraslink.controller.OrcamentoController;
import com.obraslink.model.SolicitacaoOrcamento;
import com.obraslink.model.SolicitacaoOrcamentoAnexo;
import com.obraslink.model.SolicitacaoOrcamentoItem;
import com.obraslink.service.OrcamentoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Pedido de orçamento a vários prestadores da mesma categoria (ver {@link OrcamentoController}). */
@SpringJUnitConfig({TermosSecurityTest.Config.class, OrcamentoTelaTest.Config.class})
@WebAppConfiguration
class OrcamentoTelaTest {
    @Configuration
    @Import(OrcamentoController.class)
    static class Config {
        @Bean OrcamentoService orcamentoService() { return mock(OrcamentoService.class); }
    }

    @Autowired WebApplicationContext context;
    @Autowired OrcamentoService service;
    MockMvc mvc;

    @BeforeEach
    void preparar() {
        reset(service);
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    private SolicitacaoOrcamento solicitacao() {
        SolicitacaoOrcamento s = new SolicitacaoOrcamento();
        s.setId(100L);
        s.setCategoriasNomes(new java.util.ArrayList<>(List.of("Pintor")));
        return s;
    }

    private SolicitacaoOrcamento solicitacaoPendente() {
        SolicitacaoOrcamento s = new SolicitacaoOrcamento();
        s.setId(100L);
        s.setCategoriasNomes(new java.util.ArrayList<>());
        return s;
    }

    @Test
    void iniciarRedirecionaParaOFormularioQuandoJaTemUmaEspecialidade() throws Exception {
        when(service.iniciar(List.of(10L, 11L))).thenReturn(100L);
        when(service.consultarFormulario(100L)).thenReturn(solicitacao());
        mvc.perform(get("/orcamentos/novo").param("prestadorId", "10", "11"))
                .andExpect(redirectedUrl("/orcamentos/100/formulario"));
    }

    @Test
    void iniciarRedirecionaParaEspecialidadesQuandoHaMaisDeUmaEmComum() throws Exception {
        when(service.iniciar(List.of(10L))).thenReturn(100L);
        when(service.consultarFormulario(100L)).thenReturn(solicitacaoPendente());
        mvc.perform(get("/orcamentos/novo").param("prestadorId", "10"))
                .andExpect(redirectedUrl("/orcamentos/100/especialidades"));
    }

    @Test
    void iniciarComErroVoltaParaAVitrineComAMensagem() throws Exception {
        when(service.iniciar(any())).thenThrow(new IllegalArgumentException("Selecione prestadores que tenham ao menos uma especialidade em comum."));
        mvc.perform(get("/orcamentos/novo").param("prestadorId", "10", "12"))
                .andExpect(redirectedUrl("/prestadores"))
                .andExpect(flash().attribute("mensagemErro", "Selecione prestadores que tenham ao menos uma especialidade em comum."));
    }

    @Test
    void mostraAsEspecialidadesEmComumParaEscolher() throws Exception {
        when(service.consultarFormulario(100L)).thenReturn(solicitacaoPendente());
        when(service.itensDaSolicitacao(100L)).thenReturn(List.of(
                SolicitacaoOrcamentoItem.builder().id(1L).prestadorId(10L).prestadorNome("Prestador Um").mensagem("").build()));
        when(service.especialidadesComunsDaSolicitacao(100L)).thenReturn(List.of(
                com.obraslink.model.Categoria.builder().id(5L).nome("Pintor").build(),
                com.obraslink.model.Categoria.builder().id(6L).nome("Eletricista").build()));
        String html = mvc.perform(get("/orcamentos/100/especialidades")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertTrueContains(html, "Prestador Um");
        assertTrueContains(html, "Pintor");
        assertTrueContains(html, "Eletricista");
    }

    @Test
    void jaTendoEspecialidadeEscolhidaPulaDiretoParaOFormulario() throws Exception {
        when(service.consultarFormulario(100L)).thenReturn(solicitacao());
        mvc.perform(get("/orcamentos/100/especialidades")).andExpect(redirectedUrl("/orcamentos/100/formulario"));
    }

    @Test
    void escolherEspecialidadesRedirecionaParaOFormularioOuVoltaComErro() throws Exception {
        mvc.perform(post("/orcamentos/100/especialidades").param("categoriaId", "5", "6"))
                .andExpect(redirectedUrl("/orcamentos/100/formulario"));
        verify(service).escolherEspecialidades(100L, List.of(5L, 6L));

        doThrow(new IllegalArgumentException("Selecione ao menos uma especialidade.")).when(service).escolherEspecialidades(eq(100L), any());
        mvc.perform(post("/orcamentos/100/especialidades"))
                .andExpect(redirectedUrl("/orcamentos/100/especialidades"))
                .andExpect(flash().attribute("mensagemErro", "Selecione ao menos uma especialidade."));
    }

    @Test
    void mostraOFormularioComOsCamposDaCategoria() throws Exception {
        when(service.consultarFormulario(100L)).thenReturn(solicitacao());
        when(service.itensDaSolicitacao(100L)).thenReturn(List.of(
                SolicitacaoOrcamentoItem.builder().id(1L).prestadorId(10L).prestadorNome("Prestador Um").mensagem("").build()));
        when(service.anexosDaSolicitacao(100L)).thenReturn(List.of());
        String html = mvc.perform(get("/orcamentos/100/formulario")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertTrueContains(html, "Prestador Um");
        assertTrueContains(html, "Área aproximada");
        assertTrueContains(html, "enctype=\"multipart/form-data\"");
    }

    @Test
    void mostraOEnderecoCadastradoComoPadraoEOCampoParaOutroEndereco() throws Exception {
        when(service.consultarFormulario(100L)).thenReturn(solicitacao());
        when(service.itensDaSolicitacao(100L)).thenReturn(List.of());
        when(service.anexosDaSolicitacao(100L)).thenReturn(List.of());
        var endereco = com.obraslink.model.Endereco.builder().logradouro("Rua do Cadastro").numero("10")
                .bairro("Centro").cidade("Curitiba").estado("PR").build();
        when(service.enderecoCadastradoAtual()).thenReturn(endereco);
        String html = mvc.perform(get("/orcamentos/100/formulario")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertTrueContains(html, "Usar o endereço do meu perfil");
        assertTrueContains(html, "Rua do Cadastro, 10 - Centro, Curitiba/PR");
        assertTrueContains(html, "Usar outro endereço");
        assertTrueContains(html, "name=\"usarEnderecoCadastrado\"");
    }

    @Test
    void semEnderecoCadastradoMostraOCampoParaInformarUm() throws Exception {
        when(service.consultarFormulario(100L)).thenReturn(solicitacao());
        when(service.itensDaSolicitacao(100L)).thenReturn(List.of());
        when(service.anexosDaSolicitacao(100L)).thenReturn(List.of());
        when(service.enderecoCadastradoAtual()).thenReturn(null);
        String html = mvc.perform(get("/orcamentos/100/formulario")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertTrueContains(html, "ainda não tem um endereço cadastrado");
        assertFalseContains(html, "Usar o endereço do meu perfil");
    }

    private static void assertFalseContains(String html, String trecho) {
        org.junit.jupiter.api.Assertions.assertFalse(html.contains(trecho), () -> "não devia conter: " + trecho);
    }

    @Test
    void salvarRedirecionaParaRevisaoOuVoltaComErro() throws Exception {
        mvc.perform(post("/orcamentos/100/formulario").param("usarEnderecoCadastrado", "true").param("descricaoGeral", "Pintar"))
                .andExpect(redirectedUrl("/orcamentos/100/revisar"));

        doThrow(new IllegalArgumentException("Informe o endereço de execução.")).when(service).salvarFormulario(eq(100L), any(), any());
        mvc.perform(post("/orcamentos/100/formulario"))
                .andExpect(redirectedUrl("/orcamentos/100/formulario"))
                .andExpect(flash().attribute("mensagemErro", "Informe o endereço de execução."));
    }

    @Test
    void mostraARevisaoComUmaMensagemPorPrestador() throws Exception {
        when(service.consultarFormulario(100L)).thenReturn(solicitacao());
        when(service.itensDaSolicitacao(100L)).thenReturn(List.of(
                SolicitacaoOrcamentoItem.builder().id(1L).prestadorId(10L).prestadorNome("Prestador Um").mensagem("Olá, sou Cliente...").build()));
        String html = mvc.perform(get("/orcamentos/100/revisar")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertTrueContains(html, "Prestador Um");
        assertTrueContains(html, "Olá, sou Cliente...");
        assertTrueContains(html, "mensagem_1");
    }

    @Test
    void confirmarEnviaAsMensagensEditadasEMostraOsLinks() throws Exception {
        when(service.confirmarEnvio(eq(100L), anyMap())).thenReturn(List.of(
                new OrcamentoService.EnvioResultado("Prestador Um", "https://wa.me/5511999999999?text=oi")));
        String html = mvc.perform(post("/orcamentos/100/confirmar").param("mensagem_1", "texto ajustado"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrueContains(html, "Prestador Um");
        assertTrueContains(html, "https://wa.me/5511999999999?text=oi");
        verify(service).confirmarEnvio(eq(100L), argThat(m -> "texto ajustado".equals(m.get(1L))));
    }

    @Test
    void confirmarComErroVoltaParaARevisao() throws Exception {
        when(service.confirmarEnvio(eq(100L), anyMap())).thenThrow(new IllegalArgumentException("Preencha o formulário antes de enviar."));
        mvc.perform(post("/orcamentos/100/confirmar"))
                .andExpect(redirectedUrl("/orcamentos/100/revisar"))
                .andExpect(flash().attribute("mensagemErro", "Preencha o formulário antes de enviar."));
    }

    @Test
    void excluirAnexoVoltaParaOFormulario() throws Exception {
        mvc.perform(post("/orcamentos/100/anexos/5/excluir"))
                .andExpect(redirectedUrl("/orcamentos/100/formulario"));
        verify(service).excluirAnexo(100L, 5L);
    }

    private static void assertTrueContains(String html, String trecho) {
        org.junit.jupiter.api.Assertions.assertTrue(html.contains(trecho), () -> "esperava conter: " + trecho);
    }
}
