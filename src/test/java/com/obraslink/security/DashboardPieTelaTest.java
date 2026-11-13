package com.obraslink.security;

import com.obraslink.controller.AuthController;
import com.obraslink.controller.DashboardController;
import com.obraslink.model.Atendimento;
import com.obraslink.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Painel: gráfico de pizza (em vez da barra) com a situação dos atendimentos. Cada fatia e cada item da
 * legenda carregam o status em data-status-fatia; o clique (tratado em dashboard.js) filtra as linhas ocultas
 * em #status-detalhe-dados e preenche o card #status-detalhe. Aqui conferimos apenas o HTML gerado pelo servidor.
 */
@SpringJUnitConfig({TermosSecurityTest.Config.class, DashboardPieTelaTest.Config.class})
@WebAppConfiguration
class DashboardPieTelaTest {
    @Configuration
    @Import({AuthController.class, DashboardController.class})
    static class Config {
        @Bean AuthService authService() { return mock(AuthService.class); }
        @Bean CategoriaService categoriaService() { return mock(CategoriaService.class); }
        @Bean DestaquesService destaquesService() { return mock(DestaquesService.class); }
        @Bean IndicadoresPlataformaService indicadores() { return mock(IndicadoresPlataformaService.class); }
        @Bean DashboardService dashboardService() { return mock(DashboardService.class); }
    }

    @Autowired WebApplicationContext context;
    @Autowired DashboardService dashboard;

    private Atendimento atendimento(long id, Atendimento.Status status, String cliente) {
        Atendimento a = new Atendimento();
        a.setId(id);
        a.setStatus(status);
        a.setClienteNome(cliente);
        a.setCriadoEm(LocalDateTime.of(2026, 11, 10, 9, 0));
        return a;
    }

    @BeforeEach
    void preparar() {
        reset(dashboard);
        when(dashboard.usuarioTemPerfilPrestador()).thenReturn(true);
        when(dashboard.dataInicioPadrao()).thenReturn(LocalDate.now());
        when(dashboard.dataFimPadrao()).thenReturn(LocalDate.now());
    }

    @Test
    void graficoDePizzaTemUmaFatiaClicavelPorStatusEALegendaTambemLevaOStatus() throws Exception {
        var a = atendimento(1, Atendimento.Status.CONTATO_SOLICITADO, "Ana");
        var b = atendimento(2, Atendimento.Status.EM_ANDAMENTO, "Bia");
        var c = atendimento(3, Atendimento.Status.CONCLUIDO, "Caio");
        var todos = List.of(a, b, c);
        when(dashboard.carregar(any(), any())).thenReturn(new DashboardService.Resumo(3, 1, 1,
                List.of(new DashboardService.Barra("Aguardando confirmação", 1, 33),
                        new DashboardService.Barra("Em andamento", 1, 33),
                        new DashboardService.Barra("Concluídos", 1, 34)),
                List.of(), todos, todos));

        String html = MockMvcBuilders.webAppContextSetup(context).build().perform(get("/dashboard"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertTrue(html.contains("class=\"pie-chart\""), "o painel mostra um gráfico de pizza");
        assertFalse(html.contains("stacked-bar"), "a barra empilhada antiga foi substituída");
        assertTrue(html.contains("data-status-fatia=\"CONTATO_SOLICITADO\""));
        assertTrue(html.contains("data-status-fatia=\"EM_ANDAMENTO\""));
        assertTrue(html.contains("data-status-fatia=\"CONCLUIDO\""));
        // legenda: mesmos três status, clicáveis (tabindex para teclado)
        assertEquals(6, html.split("data-status-fatia=", -1).length - 1, "3 fatias no gráfico + 3 itens na legenda");
        assertTrue(html.contains("role=\"button\""));
        // card oculto que vai receber o detalhe ao clicar
        assertTrue(html.contains("id=\"status-detalhe\"") && html.contains("hidden"));
        // dados ocultos de todos os atendimentos do período, um <tr data-status=...> por atendimento
        assertTrue(html.contains("id=\"status-detalhe-dados\""));
        assertTrue(html.contains("data-status=\"CONTATO_SOLICITADO\"") && html.contains(">Ana<"));
        assertTrue(html.contains("data-status=\"EM_ANDAMENTO\"") && html.contains(">Bia<"));
        assertTrue(html.contains("data-status=\"CONCLUIDO\"") && html.contains(">Caio<"));
        // botão "ver mais" para paginar o detalhe (dashboard.js), começa escondido
        int indiceDetalhe = html.indexOf("id=\"status-detalhe\"");
        int indiceVerMais = html.indexOf("data-status-detalhe-vermais", indiceDetalhe);
        assertTrue(indiceVerMais > indiceDetalhe && indiceVerMais < html.indexOf("</section>", indiceDetalhe), "botão de ver mais dentro do card de detalhe");
        assertTrue(html.substring(indiceVerMais - 10, indiceVerMais + 40).contains("hidden"), "ver mais começa escondido");
        // a seção de detalhe fica depois do grid de 2 colunas (gráfico + meses), não dentro dele, senão distorce o layout
        assertTrue(indiceDetalhe > html.indexOf("Meses com mais contatos"), "detalhe fica depois do dash-grid, não dentro dele");
    }

    @Test
    void statusSemAtendimentoNaoGeraFatiaNemItemDeLegenda() throws Exception {
        var c = atendimento(1, Atendimento.Status.CONCLUIDO, "Caio");
        var todos = List.of(c);
        when(dashboard.carregar(any(), any())).thenReturn(new DashboardService.Resumo(1, 0, 1,
                List.of(new DashboardService.Barra("Aguardando confirmação", 0, 0),
                        new DashboardService.Barra("Em andamento", 0, 0),
                        new DashboardService.Barra("Concluídos", 1, 100)),
                List.of(), todos, todos));

        String html = MockMvcBuilders.webAppContextSetup(context).build().perform(get("/dashboard"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertFalse(html.contains("data-status-fatia=\"CONTATO_SOLICITADO\""), "sem atendimento aguardando: sem fatia nem legenda");
        assertFalse(html.contains("data-status-fatia=\"EM_ANDAMENTO\""));
        assertTrue(html.contains("data-status-fatia=\"CONCLUIDO\""));
    }
}
