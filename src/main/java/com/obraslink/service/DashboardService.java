package com.obraslink.service;

import com.obraslink.model.*;
import com.obraslink.repository.AtendimentoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
public class DashboardService {
    private final AcessoService acesso;
    private final AtendimentoRepository atendimentos;
    public record Barra(String titulo, long valor, int percentual) {}
    public record Resumo(long total, long andamento, long concluidos, List<Barra> status,
                         List<Barra> meses, List<Atendimento> recentes) {}

    @Transactional(readOnly = true)
    public Resumo carregar() {
        Usuario usuario = acesso.getUsuarioAtualOrThrow();
        if (!Boolean.TRUE.equals(usuario.getAtivo()) || !usuario.getPapeis().contains(PapelUsuario.PRESTADOR)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        var lista = atendimentos.findByPrestadorUsuarioIdOrderByIdDesc(usuario.getId());
        long total = lista.size();
        long andamento = lista.stream().filter(a -> a.getStatus() == Atendimento.Status.EM_ANDAMENTO).count();
        long concluidos = lista.stream().filter(a -> a.getStatus() == Atendimento.Status.CONCLUIDO).count();
        var status = new ArrayList<>(List.of(barra("Aguardando confirmação", total - andamento - concluidos, total),
                barra("Em andamento", andamento, total), barra("Concluídos", concluidos, total)));
        var meses = new ArrayList<Barra>();
        YearMonth atual = YearMonth.now(java.time.ZoneId.of("America/Sao_Paulo"));
        long[] contagens = new long[6];
        for (int i = 0; i < 6; i++) {
            YearMonth mes = atual.minusMonths(5 - i);
            contagens[i] = lista.stream().filter(a -> a.getCriadoEm() != null && YearMonth.from(a.getCriadoEm()).equals(mes)).count();
        }
        long totalPeriodo = Arrays.stream(contagens).sum();
        for (int i = 0; i < 6; i++) {
            meses.add(barra(atual.minusMonths(5-i).format(DateTimeFormatter.ofPattern("MMM/yy", Locale.forLanguageTag("pt-BR"))), contagens[i], totalPeriodo));
        }
        status.sort(Comparator.comparingLong(Barra::valor).reversed());
        meses.sort(Comparator.comparingLong(Barra::valor).reversed());
        return new Resumo(total, andamento, concluidos, status, meses, lista.stream().limit(5).toList());
    }
    private Barra barra(String titulo, long valor, long max) {
        return new Barra(titulo, valor, max == 0 ? 0 : (int) Math.round(valor * 100.0 / max));
    }
}
