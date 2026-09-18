package com.obraslink.security;

import com.obraslink.model.Usuario;
import com.obraslink.service.AcessoService;
import com.obraslink.service.TermosService;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitConfig(TermosSecurityTest.Config.class)
@WebAppConfiguration
class TermosSecurityTest {
    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import(SecurityConfig.class)
    static class Config {
        @Bean AcessoService acessoService() { return mock(AcessoService.class); }
        @Bean TermosService termosService() { return mock(TermosService.class); }
        @Bean Endpoints endpoints() { return new Endpoints(); }
        @Bean com.obraslink.controller.TermosController termosController(TermosService termos, AcessoService acesso) {
            return new com.obraslink.controller.TermosController(termos, acesso);
        }
        @Bean Modelo modelo() { return new Modelo(); }
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
        @Bean org.springframework.security.core.userdetails.UserDetailsService userDetailsService() {
            return new org.springframework.security.provisioning.InMemoryUserDetailsManager(
                    org.springframework.security.core.userdetails.User.withUsername("cliente")
                            .password("{noop}teste").roles("CLIENTE").build());
        }
    }

    @ControllerAdvice
    static class Modelo {
        @ModelAttribute("auth")
        Map<String, Object> auth() {
            return Map.of("authenticated", true, "hasPrestador", false, "hasCliente", true,
                    "isAdmin", false, "email", "cliente@teste.com", "notificacoesNaoLidas", 0);
        }
    }

    @RestController
    static class Endpoints {
        @GetMapping({"/", "/notificacoes"})
        String pagina() { return "ok"; }
        @PostMapping("/prestadores/10/contato")
        String alterar() { return "alterado"; }
    }

    @Autowired WebApplicationContext context;
    @Autowired @Qualifier("springSecurityFilterChain") Filter security;
    @Autowired AcessoService acesso;
    @Autowired TermosService termos;
    private MockMvc mvc;
    private MockHttpSession session;

    @BeforeEach
    void preparar() {
        reset(acesso, termos);
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(security).build();
        session = new MockHttpSession();
        var securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(new UsernamePasswordAuthenticationToken("cliente", "", List.of()));
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, securityContext);
        when(acesso.getUsuarioAtual()).thenReturn(Optional.of(Usuario.builder().id(7L).build()));
        when(acesso.getUsuarioAtualOrThrow()).thenReturn(Usuario.builder().id(7L).build());
    }

    @Test
    void primeiroAcessoBloqueiaInicioEUrlDireta() throws Exception {
        mvc.perform(get("/").session(session)).andExpect(status().isSeeOther()).andExpect(header().string("Location", "/termos/aceitar"));
        mvc.perform(get("/notificacoes").session(session)).andExpect(status().isSeeOther());
    }

    @Test
    void aceitePersistidoLiberaProximosAcessos() throws Exception {
        when(termos.aceitouVersaoAtual(7L)).thenReturn(true);
        mvc.perform(get("/notificacoes").session(session)).andExpect(status().isOk());
    }

    @Test
    void documentosEFormularioContinuamAcessiveisSemAceite() throws Exception {
        mvc.perform(get("/termos").session(session)).andExpect(status().isOk());
        mvc.perform(get("/privacidade").session(session)).andExpect(status().isOk());
        mvc.perform(get("/termos/aceitar").session(session)).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Aceitar e continuar")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("checked="))));
    }

    @Test
    void visitantePodeLerDocumentosMasNaoAcessarAceite() throws Exception {
        when(acesso.getUsuarioAtual()).thenReturn(Optional.empty());
        mvc.perform(get("/termos")).andExpect(status().isOk());
        mvc.perform(get("/privacidade")).andExpect(status().isOk());
        mvc.perform(get("/termos/aceitar")).andExpect(status().isFound());
    }

    @Test
    void formularioExigeCsrfEPostDiretoNaoContornaBloqueio() throws Exception {
        mvc.perform(post("/termos/aceitar").session(session)).andExpect(status().isForbidden());
        var result = mvc.perform(get("/termos/aceitar").session(session)).andReturn();
        CsrfToken token = (CsrfToken) result.getRequest().getAttribute(CsrfToken.class.getName());
        String valor = token.getToken();
        mvc.perform(post("/termos/aceitar").session(session).param(token.getParameterName(), valor)
                        .param("versao", TermosService.VERSAO_ATUAL).param("aceitouTermos", "true").param("cientePrivacidade", "true"))
                .andExpect(status().isFound()).andExpect(header().string("Location", "/"));
        verify(termos).aceitar(TermosService.VERSAO_ATUAL, true, true);
        mvc.perform(post("/prestadores/10/contato").session(session).param(token.getParameterName(), valor))
                .andExpect(status().isSeeOther()).andExpect(header().string("Location", "/termos/aceitar"));
    }

    @Test
    void podeSairSemAceitar() throws Exception {
        var result = mvc.perform(get("/termos/aceitar").session(session)).andReturn();
        CsrfToken token = (CsrfToken) result.getRequest().getAttribute(CsrfToken.class.getName());
        mvc.perform(post("/logout").session(session).param(token.getParameterName(), token.getToken()))
                .andExpect(status().isFound()).andExpect(header().string("Location", "/login?logout"));
    }
}
