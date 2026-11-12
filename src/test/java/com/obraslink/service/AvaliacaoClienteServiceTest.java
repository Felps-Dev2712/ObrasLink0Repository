package com.obraslink.service;
import com.obraslink.dto.AvaliacaoClienteForm;
import com.obraslink.model.*;
import com.obraslink.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class AvaliacaoClienteServiceTest {
 AtendimentoRepository atendimentos = mock(AtendimentoRepository.class);
 AvaliacaoClienteRepository avaliacoes = mock(AvaliacaoClienteRepository.class);
 PrestadorRepository prestadores = mock(PrestadorRepository.class);
 AcessoService acesso = mock(AcessoService.class);
 NotificacaoService notificacoes = mock(NotificacaoService.class);
 AvaliacaoClienteService service = new AvaliacaoClienteService(atendimentos, avaliacoes, prestadores, acesso, notificacoes);
 Atendimento atendimento;
 Usuario usuario;
 AvaliacaoClienteForm form;
 @BeforeEach void preparar() {
  usuario = new Usuario(); usuario.setId(40L); usuario.setAtivo(true); usuario.setPapeis(Set.of(PapelUsuario.PRESTADOR));
  when(acesso.getUsuarioAtualOrThrow()).thenReturn(usuario);
  atendimento = new Atendimento(); atendimento.setId(50L); atendimento.setClienteUsuarioId(10L); atendimento.setPrestadorUsuarioId(40L); atendimento.setPrestadorId(30L); atendimento.setStatus(Atendimento.Status.CONCLUIDO);
  when(atendimentos.findById(50L)).thenReturn(Optional.of(atendimento));
  when(atendimentos.findByIdParaAvaliar(50L)).thenReturn(Optional.of(atendimento));
  when(prestadores.findById(30L)).thenReturn(Optional.of(Prestador.builder().id(30L).usuarioId(40L).build()));
  form = new AvaliacaoClienteForm(); form.setComunicacao(5); form.setPontualidade(3); form.setCumprimentoCombinado(1);
 }
 @Test void avaliacoesRecebidasConsultaUmaVezSoEListaVaziaNaoVaiAoBanco() {
  when(avaliacoes.findAtendimentoIdsAvaliados(List.of(31L, 32L))).thenReturn(List.of(32L));
  assertEquals(Set.of(32L), service.atendimentosComAvaliacaoRecebida(List.of(31L, 32L)));
  assertTrue(service.atendimentosComAvaliacaoRecebida(List.of()).isEmpty());
  assertTrue(service.atendimentosComAvaliacaoRecebida(null).isEmpty());
  verify(avaliacoes, times(1)).findAtendimentoIdsAvaliados(any());
 }
 @Test void salvaNotasIndependentesENotificaClienteReal() {
  service.avaliar(50L, form);
  verify(avaliacoes).save(argThat(a -> a.getClienteUsuarioId()==10L && a.getPrestadorUsuarioId()==40L && a.getAtendimentoId()==50L && a.getComunicacao()==5 && a.getPontualidade()==3 && a.getCumprimentoCombinado()==1));
  verify(notificacoes).criar(eq(10L), argThat(m -> !m.contains("#") && m.contains("avaliou você pelo serviço")), eq("/atendimentos/50/avaliacao-cliente"));
 }
 @ParameterizedTest @EnumSource(value=Atendimento.Status.class, names={"CONTATO_SOLICITADO", "EM_ANDAMENTO"})
 void bloqueiaAntesDaConclusao(Atendimento.Status status) { atendimento.setStatus(status); rejeita(); }
 @Test void bloqueiaOutroPrestadorInclusiveLeitura() { usuario.setId(99L); rejeita(); assertThrows(IllegalArgumentException.class, () -> service.consultar(50L)); }
 @Test void clientePodeLerMasNaoEnviar() { usuario.setId(10L); usuario.setPapeis(Set.of(PapelUsuario.CLIENTE)); assertFalse(service.consultar(50L).podeAvaliar()); rejeita(); }
 @Test void bloqueiaContaInativa() { usuario.setAtivo(false); rejeita(); }
 @Test void bloqueiaSemPapelPrestador() { usuario.setPapeis(Set.of(PapelUsuario.CLIENTE)); rejeita(); }
 @Test void comentarioNoLimiteComQuebraDeLinhaDoNavegadorEhAceito() {
  form.setComentario("a".repeat(1000) + "\r\n" + "b".repeat(999));
  service.avaliar(50L, form);
  verify(avaliacoes).save(argThat(a -> a.getComentario().length() == 2000 && !a.getComentario().contains("\r")));
 }
 @Test void comentarioLongoMostraMensagemComOLimite() {
  form.setComentario("a".repeat(2001));
  assertEquals("O comentário deve ter no máximo 2.000 caracteres.", assertThrows(IllegalArgumentException.class, () -> service.avaliar(50L, form)).getMessage());
 }
 @Test void bloqueiaAutoavaliacao() { atendimento.setClienteUsuarioId(40L); rejeita(); }
 @Test void bloqueiaPerfilTransferido() { when(prestadores.findById(30L)).thenReturn(Optional.of(Prestador.builder().usuarioId(99L).build())); rejeita(); }
 @Test void bloqueiaDuplicataEEscondeFormulario() { when(avaliacoes.findByAtendimentoId(50L)).thenReturn(Optional.of(new AvaliacaoCliente())); rejeita(); assertFalse(service.consultar(50L).podeAvaliar()); }
 @Test void bloqueiaNotaAusente() { form.setComunicacao(null); rejeita(); }
 @Test void bloqueiaNotaForaDoIntervalo() { form.setPontualidade(6); rejeita(); }
 @Test void bloqueiaComentarioLongo() { form.setComentario("a".repeat(2001)); rejeita(); }
 @Test void bloqueiaAtendimentoInexistente() { when(atendimentos.findByIdParaAvaliar(50L)).thenReturn(Optional.empty()); rejeita(); }
 void rejeita() { assertThrows(IllegalArgumentException.class, () -> service.avaliar(50L, form)); verify(avaliacoes, never()).save(any()); verifyNoInteractions(notificacoes); }
}
