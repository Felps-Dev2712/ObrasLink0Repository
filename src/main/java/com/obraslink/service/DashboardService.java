package com.obraslink.service;

import com.obraslink.model.*;
import com.obraslink.repository.AtendimentoRepository;
import com.obraslink.repository.AvaliacaoClienteRepository;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.time.YearMonth;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class DashboardService {
    private final AcessoService acesso;
    private final AtendimentoRepository atendimentos;
    private final AvaliacaoClienteRepository avaliacoesCliente;
    private final com.obraslink.repository.PrestadorRepository prestadores;
    public record Barra(String titulo, long valor, int percentual) {}
    public record Resumo(long total, long andamento, long concluidos, List<Barra> status,
                         List<Barra> meses, List<Atendimento> recentes) {}
    public record ResumoCliente(long totalContatos, long emAndamento, long concluidos,
                                List<Atendimento> recentes, List<AvaliacaoCliente> avaliacoes) {}

    @Autowired
    public DashboardService(AcessoService acesso, AtendimentoRepository atendimentos,
                            AvaliacaoClienteRepository avaliacoesCliente,
                            com.obraslink.repository.PrestadorRepository prestadores) {
        this.acesso = acesso;
        this.atendimentos = atendimentos;
        this.avaliacoesCliente = avaliacoesCliente;
        this.prestadores = prestadores;
    }
    public DashboardService(AcessoService acesso, AtendimentoRepository atendimentos,
                            AvaliacaoClienteRepository avaliacoesCliente) {
        this(acesso, atendimentos, avaliacoesCliente, null);
    }

    // Mantém a construção simples usada nos testes focados no painel do prestador.
    public DashboardService(AcessoService acesso, AtendimentoRepository atendimentos) {
        this(acesso, atendimentos, null, null);
    }

    @Transactional(readOnly = true)
    public boolean usuarioTemPerfilCliente() {
        return acesso.getUsuarioAtual().filter(u -> Boolean.TRUE.equals(u.getAtivo())
                && u.getPapeis().contains(PapelUsuario.CLIENTE)).isPresent();
    }

    @Transactional(readOnly = true)
    public boolean usuarioTemPerfilPrestador() {
        return acesso.getUsuarioAtual().filter(u -> Boolean.TRUE.equals(u.getAtivo())
                && u.getPapeis().contains(PapelUsuario.PRESTADOR)).isPresent();
    }

    @Transactional(readOnly = true)
    public ResumoCliente carregarCliente() {
        Usuario usuario = acesso.getUsuarioAtualOrThrow();
        if (!Boolean.TRUE.equals(usuario.getAtivo()) || !usuario.getPapeis().contains(PapelUsuario.CLIENTE)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        var lista = atendimentos.findByClienteUsuarioIdOrPrestadorUsuarioIdOrderByIdDesc(usuario.getId(), usuario.getId())
                .stream().filter(a -> usuario.getId().equals(a.getClienteUsuarioId())).toList();
        long andamento = lista.stream().filter(a -> a.getStatus() == Atendimento.Status.EM_ANDAMENTO).count();
        long concluidos = lista.stream().filter(a -> a.getStatus() == Atendimento.Status.CONCLUIDO).count();
        var reviews = avaliacoesCliente == null ? List.<AvaliacaoCliente>of()
                : avaliacoesCliente.findByClienteUsuarioIdOrderByCriadoEmDesc(usuario.getId());
        return new ResumoCliente(lista.size(), andamento, concluidos, lista.stream().limit(5).toList(), reviews.stream().limit(5).toList());
    }

    @Transactional(readOnly = true)
    public Resumo carregar() {
        Usuario usuario = acesso.getUsuarioAtualOrThrow();
        return carregarPadrao(usuario);
    }

    private Resumo carregarPadrao(Usuario usuario) {
        return carregarDados(usuario, null, null);
    }

    @Transactional(readOnly = true)
    public Resumo carregar(LocalDate inicio, LocalDate fim) {
        Usuario usuario = acesso.getUsuarioAtualOrThrow();
        validarPrestador(usuario);
        if (prestadores != null && prestadores.findByUsuarioId(usuario.getId()).isEmpty()) {
            throw new IllegalArgumentException("Cadastro de prestador não encontrado.");
        }
        LocalDate cadastro = dataCadastro(usuario);
        LocalDate hoje = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        LocalDate de = inicio == null ? cadastro : inicio;
        LocalDate ate = fim == null ? hoje : fim;
        if (de.isAfter(ate)) throw new IllegalArgumentException("A data inicial não pode ser posterior à data final.");
        if (de.isBefore(cadastro)) throw new IllegalArgumentException("A data inicial não pode ser anterior à data de cadastro do prestador.");
        if (ate.isAfter(hoje)) throw new IllegalArgumentException("A data final não pode ser posterior à data atual.");
        return carregarDados(usuario, de, ate);
    }
    @Transactional(readOnly=true) public LocalDate dataInicioPadrao(){ return dataCadastro(acesso.getUsuarioAtualOrThrow()); }

    /**
     * Data de cadastro do prestador limitada a hoje (fuso de São Paulo). O criado_em é gravado no horário do servidor
     * (UTC): uma conta criada depois das 21h teria a data de amanhã, o período padrão ficaria inválido (início depois
     * do fim) e o painel entrava em loop de redirecionamento.
     */
    private LocalDate dataCadastro(Usuario usuario) {
        LocalDate hoje = LocalDate.now(ZoneId.of("America/Sao_Paulo"));
        java.time.LocalDateTime criado = prestadores == null ? usuario.getCriadoEm()
                : prestadores.findByUsuarioId(usuario.getId()).map(p -> p.getCriadoEm()).orElse(null);
        if (criado == null) return hoje;
        LocalDate data = criado.toLocalDate();
        return data.isAfter(hoje) ? hoje : data;
    }
    public LocalDate dataFimPadrao(){return LocalDate.now(ZoneId.of("America/Sao_Paulo"));}

    private Resumo carregarDados(Usuario usuario, LocalDate inicio, LocalDate fim) {
        validarPrestador(usuario);
        var lista = inicio == null ? atendimentos.findByPrestadorUsuarioIdOrderByIdDesc(usuario.getId())
                : atendimentos.findByPrestadorUsuarioIdAndCriadoEmGreaterThanEqualAndCriadoEmLessThanOrderByIdDesc(
                    usuario.getId(), inicio.atStartOfDay(), fim.plusDays(1).atStartOfDay());
        long total = lista.size();
        long andamento = lista.stream().filter(a -> a.getStatus() == Atendimento.Status.EM_ANDAMENTO).count();
        long concluidos = lista.stream().filter(a -> a.getStatus() == Atendimento.Status.CONCLUIDO).count();
        var status = new ArrayList<>(List.of(barra("Aguardando confirmação", total - andamento - concluidos, total),
                barra("Em andamento", andamento, total), barra("Concluídos", concluidos, total)));
        var meses = new ArrayList<Barra>();
        YearMonth atual = fim == null ? YearMonth.now(ZoneId.of("America/Sao_Paulo")) : YearMonth.from(fim);
        long[] contagens = new long[6];
        for (int i = 0; i < 6; i++) {
            YearMonth mes = atual.minusMonths(5 - i);
            contagens[i] = lista.stream().filter(a -> a.getCriadoEm() != null && YearMonth.from(a.getCriadoEm()).equals(mes)).count();
        }
        long totalPeriodo = Arrays.stream(contagens).sum();
        for (int i = 0; i < 6; i++) meses.add(barra(atual.minusMonths(5-i).format(DateTimeFormatter.ofPattern("MMM/yy", Locale.forLanguageTag("pt-BR"))), contagens[i], totalPeriodo));
        status.sort(Comparator.comparingLong(Barra::valor).reversed());
        meses.sort(Comparator.comparingLong(Barra::valor).reversed());
        return new Resumo(total, andamento, concluidos, status, meses, lista.stream().limit(5).toList());
    }

    private void validarPrestador(Usuario usuario) {
        if (!Boolean.TRUE.equals(usuario.getAtivo()) || !usuario.getPapeis().contains(PapelUsuario.PRESTADOR)) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }

    private Barra barra(String titulo, long valor, long max) {
        return new Barra(titulo, valor, max == 0 ? 0 : (int) Math.round(valor * 100.0 / max));
    }
}
