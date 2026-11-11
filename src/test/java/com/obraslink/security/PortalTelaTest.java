package com.obraslink.security;
import com.obraslink.controller.*;
import com.obraslink.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringJUnitConfig({TermosSecurityTest.Config.class,PortalTelaTest.Config.class}) @WebAppConfiguration
class PortalTelaTest {
 @Configuration @Import({AuthController.class,DashboardController.class}) static class Config {
  @Bean AuthService authService(){return mock(AuthService.class);}
  @Bean CategoriaService categoriaService(){return mock(CategoriaService.class);}
  @Bean DestaquesService destaquesService(){return mock(DestaquesService.class);}
  @Bean IndicadoresPlataformaService indicadores(){return mock(IndicadoresPlataformaService.class);}
  @Bean DashboardService dashboardService(){return mock(DashboardService.class);}
 }
 @Autowired WebApplicationContext context;
 @Autowired DestaquesService destaques;
 @Autowired DashboardService dashboard;
 @Autowired IndicadoresPlataformaService indicadores;
 @Test void loginRenderizaProfissionaisEFormulario() throws Exception {
  when(indicadores.carregar()).thenReturn(new IndicadoresPlataformaService.Indicadores(1,2,java.math.BigDecimal.valueOf(90),List.of(new IndicadoresPlataformaService.Destaque(1L,"Ana","Pintura",3,4.5)),null));
  String html=MockMvcBuilders.webAppContextSetup(context).build().perform(get("/login")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
  assertTrue(html.contains("login-layout"));assertTrue(html.contains("Pintura"));assertTrue(html.contains("name=\"username\""));assertTrue(html.contains("name=\"password\""));assertFalse(html.contains("usuario@email.com"));
 }
 @Test void dashboardVazioRenderizaSemErro() throws Exception {
  when(dashboard.usuarioTemPerfilPrestador()).thenReturn(true);when(dashboard.dataInicioPadrao()).thenReturn(java.time.LocalDate.now());when(dashboard.dataFimPadrao()).thenReturn(java.time.LocalDate.now());when(dashboard.carregar(any(),any())).thenReturn(new DashboardService.Resumo(0,0,0,List.of(),List.of(),List.of()));
  String html=MockMvcBuilders.webAppContextSetup(context).build().perform(get("/dashboard")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();assertTrue(html.contains("dashboard-metrics"));
 }
}
