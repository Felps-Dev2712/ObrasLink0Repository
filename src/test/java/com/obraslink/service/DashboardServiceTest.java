package com.obraslink.service;
import com.obraslink.model.*;
import com.obraslink.repository.AtendimentoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class DashboardServiceTest {
 AcessoService acesso=mock(AcessoService.class);
 AtendimentoRepository repo=mock(AtendimentoRepository.class);
 DashboardService service=new DashboardService(acesso,repo);
 Usuario usuario=Usuario.builder().id(42L).ativo(true).papeis(Set.of(PapelUsuario.PRESTADOR)).build();
 @Test void limitaConsultaAoPrestadorLogadoECalculaGraficos() {
  when(acesso.getUsuarioAtualOrThrow()).thenReturn(usuario);
  Atendimento a=new Atendimento();a.setStatus(Atendimento.Status.CONCLUIDO);a.setCriadoEm(LocalDateTime.now(ZoneId.of("America/Sao_Paulo")));
  Atendimento b=new Atendimento();b.setStatus(Atendimento.Status.EM_ANDAMENTO);b.setCriadoEm(a.getCriadoEm().minusMonths(7));
  when(repo.findByPrestadorUsuarioIdOrderByIdDesc(42L)).thenReturn(List.of(a,b));
  var resumo=service.carregar();assertEquals(2,resumo.total());assertEquals(1,resumo.concluidos());assertEquals(1,resumo.andamento());assertEquals(6,resumo.meses().size());assertEquals(1,resumo.meses().stream().mapToLong(DashboardService.Barra::valor).sum());assertEquals(50,resumo.status().get(0).percentual());
  verify(repo).findByPrestadorUsuarioIdOrderByIdDesc(42L);
 }
 @Test void ordenaPorQuantidadeECalculaParticipacaoNoPeriodo() {
  when(acesso.getUsuarioAtualOrThrow()).thenReturn(usuario);
  var data=LocalDateTime.now(ZoneId.of("America/Sao_Paulo"));
  var lista=new ArrayList<Atendimento>();
  for(int i=0;i<4;i++){var a=new Atendimento();a.setStatus(i<3?Atendimento.Status.CONCLUIDO:Atendimento.Status.EM_ANDAMENTO);a.setCriadoEm(i<3?data:data.minusMonths(1));lista.add(a);}
  when(repo.findByPrestadorUsuarioIdOrderByIdDesc(42L)).thenReturn(lista);
  var resumo=service.carregar();assertEquals(75,resumo.status().get(0).percentual());assertEquals(25,resumo.status().get(1).percentual());assertEquals(75,resumo.meses().get(0).percentual());assertEquals(25,resumo.meses().get(1).percentual());assertEquals(100,resumo.meses().stream().mapToInt(DashboardService.Barra::percentual).sum());
 }
 @Test void contaSemAtendimentosRetornaZeros() {when(acesso.getUsuarioAtualOrThrow()).thenReturn(usuario);when(repo.findByPrestadorUsuarioIdOrderByIdDesc(42L)).thenReturn(List.of());var resumo=service.carregar();assertEquals(0,resumo.total());assertTrue(resumo.meses().stream().allMatch(b->b.percentual()==0));}
 @Test void clienteNaoPodeConsultar() {usuario.setPapeis(Set.of(PapelUsuario.CLIENTE));when(acesso.getUsuarioAtualOrThrow()).thenReturn(usuario);assertThrows(ResponseStatusException.class,()->service.carregar());verifyNoInteractions(repo);}
 @Test void contaInativaNaoPodeConsultar() {usuario.setAtivo(false);when(acesso.getUsuarioAtualOrThrow()).thenReturn(usuario);assertThrows(ResponseStatusException.class,()->service.carregar());verifyNoInteractions(repo);}
}
