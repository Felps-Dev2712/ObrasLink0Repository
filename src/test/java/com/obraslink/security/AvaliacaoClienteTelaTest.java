package com.obraslink.security;
import com.obraslink.controller.AvaliacaoClienteController;
import com.obraslink.model.*;
import com.obraslink.service.AvaliacaoClienteService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringJUnitConfig({TermosSecurityTest.Config.class, AvaliacaoClienteTelaTest.Config.class})
@WebAppConfiguration
class AvaliacaoClienteTelaTest {
 @Configuration @Import(AvaliacaoClienteController.class) static class Config {
  @Bean AvaliacaoClienteService avaliacaoClienteService() { return mock(AvaliacaoClienteService.class); }
 }
 @Autowired WebApplicationContext context;
 @Autowired AvaliacaoClienteService service;
 MockMvc mvc;
 Atendimento atendimento;
 @BeforeEach void preparar() {
  reset(service); mvc = MockMvcBuilders.webAppContextSetup(context).build();
  atendimento = new Atendimento(); atendimento.setId(50L); atendimento.setClienteNome("Cliente"); atendimento.setPrestadorNome("Prestador");
 }
 @Test void renderizaNotasSeparadasSemSelecaoInicial() throws Exception {
  when(service.consultar(50L)).thenReturn(new AvaliacaoClienteService.Detalhes(atendimento,null,true));
  String html = mvc.perform(get("/atendimentos/50/avaliacao-cliente")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
  assertEquals(15, java.util.regex.Pattern.compile("type=\"radio\"").matcher(html).results().count());
  assertFalse(html.contains("checked=")); assertTrue(html.contains("type=\"submit\"")); assertFalse(html.contains("Atendimento #"));
 }
 @Test void avaliacaoRecebidaSomenteLeitura() throws Exception {
  AvaliacaoCliente avaliacao = new AvaliacaoCliente(); avaliacao.setComunicacao(5); avaliacao.setPontualidade(3); avaliacao.setCumprimentoCombinado(1); avaliacao.setComentario("<script>alert(1)</script>");
  when(service.consultar(50L)).thenReturn(new AvaliacaoClienteService.Detalhes(atendimento,avaliacao,false));
  String html = mvc.perform(get("/atendimentos/50/avaliacao-cliente")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
  assertFalse(html.contains("type=\"radio\"")); assertTrue(html.contains("5 de 5")); assertFalse(html.contains("<script>alert(1)</script>")); assertFalse(html.contains("Atendimento #"));
 }
 @Test void notasNaoNumericasPreservamFormularioSemEnviar() throws Exception {
  mvc.perform(post("/atendimentos/50/avaliacao-cliente").param("comunicacao","texto")).andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("mensagemErro","avaliacaoForm"));
  verifyNoInteractions(service);
 }
}
