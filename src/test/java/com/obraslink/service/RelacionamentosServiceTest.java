package com.obraslink.service;

import com.obraslink.model.AgendaDia;
import com.obraslink.model.PapelUsuario;
import com.obraslink.model.Prestador;
import com.obraslink.model.Usuario;
import com.obraslink.repository.AgendaDiaRepository;
import com.obraslink.repository.ClienteRepository;
import com.obraslink.repository.FavoritoRepository;
import com.obraslink.repository.PrestadorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RelacionamentosServiceTest {
    private final AcessoService acesso = mock(AcessoService.class);
    private final FavoritoRepository favoritos = mock(FavoritoRepository.class);
    private final AgendaDiaRepository agendaDias = mock(AgendaDiaRepository.class);
    private final ClienteRepository clientes = mock(ClienteRepository.class);
    private final PrestadorRepository prestadores = mock(PrestadorRepository.class);
    private final PerfilAtivoService perfil = mock(PerfilAtivoService.class);
    private final RelacionamentosService service = new RelacionamentosService(acesso, favoritos, agendaDias, clientes, prestadores, perfil);
    private Prestador prestador;

    @BeforeEach
    void preparar() {
        Usuario usuario = Usuario.builder().id(7L).email("p@x.com").senha("x").ativo(true).papeis(Set.of(PapelUsuario.PRESTADOR)).build();
        prestador = Prestador.builder().id(30L).nome("Fornecedor").usuarioId(7L).build();
        when(acesso.getUsuarioAtualOrThrow()).thenReturn(usuario);
        when(perfil.atual()).thenReturn(PapelUsuario.PRESTADOR);
        when(prestadores.findByUsuarioId(7L)).thenReturn(Optional.of(prestador));
    }

    @Test
    void agendaDoMesAtualTrazTodosOsDiasComOStatusMarcadoOuVazio() {
        YearMonth mes = YearMonth.now();
        LocalDate dia5 = mes.atDay(Math.min(5, mes.lengthOfMonth()));
        when(agendaDias.findByPrestadorIdAndDataBetweenOrderByDataAsc(30L, mes.atDay(1), mes.atEndOfMonth()))
                .thenReturn(List.of(new AgendaDia(30L, dia5, AgendaDia.Status.OCUPADO)));

        List<RelacionamentosService.DiaAgenda> dias = service.agendaDoMesAtual(30L);

        assertEquals(mes.lengthOfMonth(), dias.size());
        assertEquals(mes.atDay(1), dias.get(0).data());
        assertTrue(dias.stream().filter(d -> d.data().equals(dia5)).findFirst().map(d -> d.status() == AgendaDia.Status.OCUPADO).orElse(false));
        assertTrue(dias.stream().filter(d -> !d.data().equals(dia5)).allMatch(d -> d.status() == null));
    }

    @Test
    void minhaAgendaMesAtualExigePerfilDePrestadorAtivo() {
        when(perfil.atual()).thenReturn(PapelUsuario.CLIENTE);
        assertThrows(IllegalArgumentException.class, service::minhaAgendaMesAtual);
    }

    @Test
    void salvarAgendaRecusaHorarioComercialIncompleto() {
        var ex = assertThrows(IllegalArgumentException.class,
                () -> service.salvarAgenda(LocalTime.of(8, 0), null, List.of()));
        assertTrue(ex.getMessage().contains("início e o fim"));
        verifyNoInteractions(agendaDias);
    }

    @Test
    void salvarAgendaRecusaHorarioComercialInvertido() {
        var ex = assertThrows(IllegalArgumentException.class,
                () -> service.salvarAgenda(LocalTime.of(18, 0), LocalTime.of(8, 0), List.of()));
        assertTrue(ex.getMessage().contains("antes de terminar"));
    }

    @Test
    void salvarAgendaGravaOHorarioComercialNoPrestador() {
        service.salvarAgenda(LocalTime.of(8, 0), LocalTime.of(18, 0), List.of());

        assertEquals(LocalTime.of(8, 0), prestador.getHorarioComercialInicio());
        assertEquals(LocalTime.of(18, 0), prestador.getHorarioComercialFim());
        verify(prestadores).save(prestador);
    }

    @Test
    void salvarAgendaAceitaHorarioComercialVazio() {
        prestador.setHorarioComercialInicio(LocalTime.of(8, 0));
        prestador.setHorarioComercialFim(LocalTime.of(18, 0));

        service.salvarAgenda(null, null, List.of());

        assertNull(prestador.getHorarioComercialInicio());
        assertNull(prestador.getHorarioComercialFim());
    }

    @Test
    void salvarAgendaForcaOFlushEntreDeletarEGravarParaNaoViolarAChaveUnica() {
        // Sem o flush, o Hibernate agruparia os inserts antes dos deletes (ordem fixa da action queue) e
        // tentaria recriar um dia que já existe antes do delete ser aplicado, violando a chave única.
        service.salvarAgenda(null, null, List.of(new AgendaDia(null, YearMonth.now().atDay(1), AgendaDia.Status.LIVRE)));

        var ordem = inOrder(agendaDias);
        ordem.verify(agendaDias).deleteByPrestadorIdAndDataBetween(eq(30L), any(), any());
        ordem.verify(agendaDias).flush();
        ordem.verify(agendaDias).saveAll(any());
    }

    @Test
    void salvarAgendaIgnoraDiasSemInformacaoEDeOutroMes() {
        YearMonth mes = YearMonth.now();
        LocalDate deOutroMes = mes.minusMonths(2).atDay(1);
        List<AgendaDia> itens = List.of(
                new AgendaDia(null, mes.atDay(1), AgendaDia.Status.LIVRE),
                new AgendaDia(null, mes.atDay(2), AgendaDia.Status.SEM_INFORMACAO),
                new AgendaDia(null, deOutroMes, AgendaDia.Status.OCUPADO));

        service.salvarAgenda(null, null, itens);

        var captor = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(agendaDias).saveAll(captor.capture());
        List<AgendaDia> salvos = captor.getValue();
        assertEquals(1, salvos.size());
        assertEquals(mes.atDay(1), salvos.get(0).getData());
        assertEquals(30L, salvos.get(0).getPrestadorId());
        verify(agendaDias).deleteByPrestadorIdAndDataBetween(30L, mes.atDay(1), mes.atEndOfMonth());
    }
}
