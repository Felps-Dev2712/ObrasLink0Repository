package com.obraslink.service;

import com.obraslink.controller.DashboardController;
import com.obraslink.model.PapelUsuario;
import com.obraslink.model.Prestador;
import com.obraslink.model.Usuario;
import com.obraslink.repository.AtendimentoRepository;
import com.obraslink.repository.PrestadorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Painel do prestador: período padrão válido e sem loop de redirecionamento. */
class DashboardPeriodoTest {
    private static final ZoneId SP = ZoneId.of("America/Sao_Paulo");
    private final AcessoService acesso = mock(AcessoService.class);
    private final AtendimentoRepository atendimentos = mock(AtendimentoRepository.class);
    private final PrestadorRepository prestadores = mock(PrestadorRepository.class);
    private final DashboardService service = new DashboardService(acesso, atendimentos, null, prestadores);
    private final Usuario usuario = Usuario.builder().id(42L).ativo(true).papeis(Set.of(PapelUsuario.PRESTADOR)).build();

    @BeforeEach
    void preparar() {
        when(acesso.getUsuarioAtualOrThrow()).thenReturn(usuario);
        when(atendimentos.findByPrestadorUsuarioIdOrderByIdDesc(42L)).thenReturn(List.of());
    }

    @Test
    void contaCriadaANoiteComDataDeAmanhaEmUtcUsaHojeComoInicioEAbreOPainel() {
        // criado_em gravado em UTC: depois das 21h de São Paulo já é o dia seguinte
        LocalDateTime amanhaUtc = LocalDateTime.now(SP).plusDays(1).withHour(1);
        when(prestadores.findByUsuarioId(42L)).thenReturn(Optional.of(Prestador.builder().id(30L).criadoEm(amanhaUtc).build()));

        LocalDate hoje = LocalDate.now(SP);
        assertEquals(hoje, service.dataInicioPadrao(), "início nunca depois de hoje");
        assertDoesNotThrow(() -> service.carregar(service.dataInicioPadrao(), service.dataFimPadrao()));
    }

    @Test
    void contaAntigaContinuaComeçandoNaDataDeCadastro() {
        LocalDateTime cadastro = LocalDateTime.now(SP).minusMonths(3);
        when(prestadores.findByUsuarioId(42L)).thenReturn(Optional.of(Prestador.builder().id(30L).criadoEm(cadastro).build()));
        assertEquals(cadastro.toLocalDate(), service.dataInicioPadrao());
    }

    @Test
    void erroNoPeriodoPadraoVaiParaOInicioEmVezDeRedirecionarParaOPainelDeNovo() {
        DashboardService falha = mock(DashboardService.class);
        LocalDate hoje = LocalDate.now(SP);
        when(falha.usuarioTemPerfilPrestador()).thenReturn(true);
        when(falha.dataInicioPadrao()).thenReturn(hoje);
        when(falha.dataFimPadrao()).thenReturn(hoje);
        when(falha.carregar(any(), any())).thenThrow(new IllegalArgumentException("Cadastro de prestador não encontrado."));
        var controller = new DashboardController(falha);
        var redirect = new RedirectAttributesModelMap();

        String destino = controller.dashboard(null, null, new ExtendedModelMap(), redirect);
        assertEquals("redirect:/", destino, "sem loop: o período padrão já falhou");
        assertEquals("Cadastro de prestador não encontrado.", redirect.getFlashAttributes().get("mensagemErro"));

        String outroPeriodo = controller.dashboard(hoje.minusDays(5).toString(), hoje.toString(), new ExtendedModelMap(), new RedirectAttributesModelMap());
        assertTrue(outroPeriodo.startsWith("redirect:/dashboard?inicio="), "período escolhido inválido volta para o padrão");
    }
}
