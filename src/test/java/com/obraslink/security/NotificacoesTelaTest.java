package com.obraslink.security;

import com.obraslink.controller.NotificacaoController;
import com.obraslink.model.Atendimento;
import com.obraslink.model.Usuario;
import com.obraslink.service.AcessoService;
import com.obraslink.service.AtendimentoService;
import com.obraslink.service.AvaliacaoClienteService;
import com.obraslink.service.NotificacaoService;
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
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Tela "Atendimentos e notificações": botão "Ver avaliação recebida" só quando o prestador já avaliou. */
@SpringJUnitConfig(NotificacoesTelaTest.Config.class)
@WebAppConfiguration
class NotificacoesTelaTest {
    @Configuration
    @EnableWebMvc
    @Import(NotificacaoController.class)
    static class Config {
        @Bean AcessoService acessoService() { return mock(AcessoService.class); }
        @Bean NotificacaoService notificacaoService() { return mock(NotificacaoService.class); }
        @Bean AtendimentoService atendimentoService() { return mock(AtendimentoService.class); }
        @Bean AvaliacaoClienteService avaliacaoClienteService() { return mock(AvaliacaoClienteService.class); }
        @Bean com.obraslink.service.AvaliacaoPrestadorService avaliacaoPrestadorService() { return mock(com.obraslink.service.AvaliacaoPrestadorService.class); }
        @Bean TermosSecurityTest.Modelo modelo() { return new TermosSecurityTest.Modelo(); }
        @Bean org.thymeleaf.spring6.view.ThymeleafViewResolver viewResolver(org.springframework.context.ApplicationContext context) {
            var templates = new org.thymeleaf.spring6.templateresolver.SpringResourceTemplateResolver();
            templates.setApplicationContext(context);
            templates.setPrefix("classpath:/templates/");
            templates.setSuffix(".html");
            templates.setCharacterEncoding("UTF-8");
            var engine = new org.thymeleaf.spring6.SpringTemplateEngine();
            engine.setTemplateResolver(templates);
            var resolver = new org.thymeleaf.spring6.view.ThymeleafViewResolver();
            resolver.setTemplateEngine(engine);
            resolver.setCharacterEncoding("UTF-8");
            return resolver;
        }
    }

    @Autowired WebApplicationContext context;
    @Autowired AcessoService acesso;
    @Autowired AtendimentoService atendimentos;
    @Autowired AvaliacaoClienteService avaliacoes;
    @Autowired NotificacaoService notificacoes;
    @Autowired com.obraslink.service.AvaliacaoPrestadorService avaliacoesDoProfissional;
    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        reset(acesso, atendimentos, avaliacoes, notificacoes, avaliacoesDoProfissional);
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        when(acesso.getUsuarioAtualOrThrow()).thenReturn(Usuario.builder().id(7L).build());
        when(notificacoes.listarEMarcarComoLidas(7L)).thenReturn(List.of());
    }

    private static Atendimento concluidoComoCliente(long id) {
        Atendimento a = new Atendimento();
        a.setId(id);
        a.setClienteUsuarioId(7L);
        a.setPrestadorUsuarioId(40L);
        a.setPrestadorId(30L);
        a.setClienteNome("Cliente");
        a.setPrestadorNome("Profissional " + id);
        a.setStatus(Atendimento.Status.CONCLUIDO);
        a.setCriadoEm(LocalDateTime.of(2026, 11, 1, 10, 0));
        a.setConcluidoEm(LocalDateTime.of(2026, 11, 2, 10, 0));
        return a;
    }

    private String pagina() throws Exception {
        return mvc.perform(get("/notificacoes")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    @Test
    void semAvaliacaoDoPrestadorNaoMostraVerAvaliacaoRecebidaMasMantemAvaliarProfissional() throws Exception {
        when(atendimentos.listar()).thenReturn(List.of(concluidoComoCliente(31L)));
        when(avaliacoes.atendimentosComAvaliacaoRecebida(anyCollection())).thenReturn(Set.of());

        String html = pagina();
        assertFalse(html.contains("Ver avaliação recebida"));
        assertTrue(html.contains("Avaliar profissional"));
    }

    @Test
    void comAvaliacaoDoPrestadorMostraOBotaoSoNoAtendimentoAvaliado() throws Exception {
        when(atendimentos.listar()).thenReturn(List.of(concluidoComoCliente(31L), concluidoComoCliente(32L)));
        when(avaliacoes.atendimentosComAvaliacaoRecebida(anyCollection())).thenReturn(Set.of(32L));

        String html = pagina();
        assertEquals(1, html.split("Ver avaliação recebida", -1).length - 1);
        assertTrue(html.contains("/atendimentos/32/avaliacao-cliente"));
        assertFalse(html.contains("/atendimentos/31/avaliacao-cliente"));
        assertEquals(2, html.split("Avaliar profissional", -1).length - 1);
    }

    @Test
    void consultaAsAvaliacoesSoDosAtendimentosConcluidosEmQueOUsuarioEhOCliente() throws Exception {
        Atendimento emAndamento = concluidoComoCliente(33L);
        emAndamento.setStatus(Atendimento.Status.EM_ANDAMENTO);
        emAndamento.setConcluidoEm(null);
        Atendimento comoPrestador = concluidoComoCliente(34L);
        comoPrestador.setClienteUsuarioId(99L);
        comoPrestador.setPrestadorUsuarioId(7L);
        when(atendimentos.listar()).thenReturn(List.of(concluidoComoCliente(31L), emAndamento, comoPrestador));
        when(avaliacoes.atendimentosComAvaliacaoRecebida(anyCollection())).thenReturn(Set.of());

        pagina();
        verify(avaliacoes).atendimentosComAvaliacaoRecebida(List.of(31L));
    }

    // ---------- Visão do prestador ----------

    private static final java.util.Map<String, Object> AUTH_PRESTADOR = java.util.Map.of("authenticated", true, "hasPrestador", true,
            "hasCliente", false, "isAdmin", false, "perfilAtivo", "PRESTADOR", "prestadorId", 30L, "usuarioId", 7L,
            "email", "prestador@teste.com", "notificacoesNaoLidas", 0);

    private String paginaComoPrestador() throws Exception {
        String html = mvc.perform(get("/notificacoes").flashAttr("auth", AUTH_PRESTADOR)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return org.springframework.web.util.HtmlUtils.htmlUnescape(html);
    }

    private static Atendimento emAndamentoComoPrestador(long id, java.time.LocalDate data) {
        Atendimento a = concluidoComoCliente(id);
        a.setClienteUsuarioId(99L);
        a.setPrestadorUsuarioId(7L);
        a.setClienteNome("Camille Cliente");
        a.setServicoTitulo("Troca de quadro");
        a.setStatus(Atendimento.Status.EM_ANDAMENTO);
        a.setConfirmadoEm(LocalDateTime.of(2026, 11, 1, 12, 0));
        a.setConcluidoEm(null);
        a.setDataAgendada(data);
        a.setHorarioAgendado(java.time.LocalTime.of(23, 59));
        return a;
    }

    @Test
    void prestadorSemAtendimentosVeTextosVoltadosParaQuemRecebeSolicitacoes() throws Exception {
        when(atendimentos.listar()).thenReturn(List.of());
        String html = paginaComoPrestador();
        assertTrue(html.contains("Aqui chegam as solicitações dos clientes"));
        assertTrue(html.contains("Você ainda não recebeu solicitações"));
        assertTrue(html.contains("href=\"/prestadores/30\"") && html.contains("Ver meu perfil público"));
        assertFalse(html.contains("O contato registra seu interesse"));
        assertFalse(html.contains("Encontrar profissionais"));
    }

    @Test
    void clienteContinuaComOsTextosDoCliente() throws Exception {
        when(atendimentos.listar()).thenReturn(List.of());
        String html = org.springframework.web.util.HtmlUtils.htmlUnescape(pagina());
        assertTrue(html.contains("O contato registra seu interesse"));
        assertTrue(html.contains("Você ainda não possui atendimentos") && html.contains("Encontrar profissionais"));
        assertFalse(html.contains("Você ainda não recebeu solicitações"));
    }

    @Test
    void servicoAgendadoNoFuturoBloqueiaConcluirEOfereceAlterarData() throws Exception {
        var futuro = java.time.LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo")).plusDays(10);
        when(atendimentos.listar()).thenReturn(List.of(emAndamentoComoPrestador(31L, futuro)));
        when(avaliacoes.atendimentosComAvaliacaoRecebida(anyCollection())).thenReturn(Set.of());

        String html = paginaComoPrestador();
        var botaoConcluir = java.util.regex.Pattern.compile("<button[^>]*>\\s*Marcar como concluído").matcher(html);
        assertTrue(botaoConcluir.find());
        assertTrue(botaoConcluir.group().contains("disabled=\"disabled\""), "concluir desabilitado antes do dia agendado: " + botaoConcluir.group());
        assertTrue(html.contains("Disponível a partir de " + futuro.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))));
        assertTrue(html.contains("data-confirmar") && html.contains("Concluir este serviço?") && html.contains("Atendimento de Camille Cliente · Troca de quadro"));
        assertTrue(html.contains("action=\"/notificacoes/atendimentos/31/reagendar\""));
        assertTrue(html.contains("value=\"" + futuro + "\"") && html.contains("value=\"23:59\""), "formulário já vem com a data atual");
        assertFalse(html.contains("onsubmit=\"return confirm("), "sem o confirm() do navegador");
    }

    @Test
    void servicoAgendadoParaHojePodeSerConcluido() throws Exception {
        var hoje = java.time.LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo"));
        when(atendimentos.listar()).thenReturn(List.of(emAndamentoComoPrestador(32L, hoje)));
        when(avaliacoes.atendimentosComAvaliacaoRecebida(anyCollection())).thenReturn(Set.of());

        String html = paginaComoPrestador();
        assertTrue(html.contains("Marcar como concluído"));
        assertFalse(html.contains("disabled=\"disabled\""));
        assertFalse(html.contains("Disponível a partir de"));
    }

    @Test
    void reagendarRedirecionaComMensagemDeSucessoOuDeErro() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/notificacoes/atendimentos/31/reagendar")
                        .param("dataAgendada", "2026-12-01").param("horarioAgendado", "16:00"))
                .andExpect(status().is3xxRedirection()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl("/notificacoes"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash().attributeExists("mensagem"));
        verify(atendimentos).reagendar(31L, java.time.LocalDate.of(2026, 12, 1), java.time.LocalTime.of(16, 0));

        doThrow(new IllegalArgumentException("O horário do serviço já passou.")).when(atendimentos).reagendar(eq(31L), any(), any());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/notificacoes/atendimentos/31/reagendar"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash().attribute("mensagemErro", "O horário do serviço já passou."));
    }

    // ---------- Menu: "Categorias" fora do perfil do cliente ----------

    private String paginaCom(java.util.Map<String, Object> auth) throws Exception {
        when(atendimentos.listar()).thenReturn(List.of());
        return mvc.perform(get("/notificacoes").flashAttr("auth", auth)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    @Test
    void menuDoClienteNaoTemCategoriasMasMantemAsDemaisOpcoes() throws Exception {
        String html = paginaCom(java.util.Map.of("authenticated", true, "hasCliente", true, "hasPrestador", false, "isAdmin", false,
                "perfilAtivo", "CLIENTE", "usuarioId", 7L, "email", "cliente@teste.com", "notificacoesNaoLidas", 0));
        assertFalse(html.contains("href=\"/categorias\""), "sem link para Categorias no perfil do cliente");
        for (String link : List.of("href=\"/prestadores\"", "href=\"/vitrine\"", "href=\"/favoritos\"")) assertTrue(html.contains(link), link);
    }

    @Test
    void administradorContinuaVendoCategorias() throws Exception {
        String html = paginaCom(java.util.Map.of("authenticated", true, "hasCliente", false, "hasPrestador", false, "isAdmin", true,
                "perfilAtivo", "ADMIN", "usuarioId", 7L, "email", "admin@teste.com", "notificacoesNaoLidas", 0));
        assertTrue(html.contains("href=\"/categorias\""));
    }

    // ---------- Prestador: avaliação feita x avaliação recebida ----------

    private static Atendimento concluidoComoPrestador(long id) {
        Atendimento a = concluidoComoCliente(id);
        a.setClienteUsuarioId(99L);
        a.setPrestadorUsuarioId(7L);
        a.setClienteNome("Camille Cliente");
        return a;
    }

    @Test
    void prestadorTemBotoesSeparadosParaAvaliarClienteEVerAvaliacaoRecebida() throws Exception {
        // 40: nada avaliado ainda | 41: prestador já avaliou o cliente e o cliente avaliou o prestador
        when(atendimentos.listar()).thenReturn(List.of(concluidoComoPrestador(40L), concluidoComoPrestador(41L)));
        when(avaliacoes.atendimentosComAvaliacaoRecebida(anyCollection())).thenReturn(Set.of(41L));
        when(avaliacoesDoProfissional.atendimentosComAvaliacaoDoCliente(anyCollection())).thenReturn(Set.of(41L));

        String html = paginaComoPrestador();
        assertTrue(html.contains("href=\"/atendimentos/40/avaliacao-cliente\" class=\"btn btn-primary btn-sm\">Avaliar cliente"));
        assertFalse(html.contains("/atendimentos/40/avaliacao-profissional"), "sem avaliação do cliente, sem botão");
        assertTrue(html.contains("href=\"/atendimentos/41/avaliacao-cliente\" class=\"btn btn-outline btn-sm\">Ver avaliação enviada"));
        assertTrue(html.contains("href=\"/atendimentos/41/avaliacao-profissional\" class=\"btn btn-outline btn-sm\">Ver avaliação recebida"));
        assertFalse(html.contains("Avaliar cliente / ver avaliação"), "botão antigo, que misturava as duas avaliações");
    }
}
