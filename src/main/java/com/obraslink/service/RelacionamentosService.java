package com.obraslink.service;

import com.obraslink.model.AgendaDia;
import com.obraslink.model.PapelUsuario;
import com.obraslink.model.Prestador;
import com.obraslink.repository.FavoritoRepository;
import com.obraslink.repository.AgendaDiaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service @RequiredArgsConstructor
public class RelacionamentosService {
    public record FavoritoView(Long id,String nome,com.obraslink.model.Categoria categoria,java.util.List<com.obraslink.model.Categoria> categorias){}
    private final AcessoService acesso;
    private final FavoritoRepository favoritos;
    private final AgendaDiaRepository agendaDias;
    private final com.obraslink.repository.ClienteRepository clientes;
    private final com.obraslink.repository.PrestadorRepository prestadores;
    private final PerfilAtivoService perfil;
    @Transactional(readOnly=true) public java.util.List<FavoritoView> favoritosDoAtual(){
        var u=acesso.getUsuarioAtualOrThrow(); if(!Boolean.TRUE.equals(u.getAtivo())||!u.getPapeis().contains(com.obraslink.model.PapelUsuario.CLIENTE)||perfil.atual()!=com.obraslink.model.PapelUsuario.CLIENTE) throw new IllegalArgumentException("Ative o perfil de cliente para consultar favoritos.");
        Long id=clientes.findByUsuarioId(u.getId()).orElseThrow(()->new IllegalArgumentException("Cadastro de cliente não encontrado.")).getId(); return favoritos.findPrestadoresFavoritos(id).stream().map(p->new FavoritoView(p.getId(),p.getNome(),p.getCategoria(),p.getCategorias())).toList();
    }
    @Transactional(readOnly=true) public java.util.Set<Long> idsFavoritosDoAtual(){
        var u=acesso.getUsuarioAtual().orElse(null);
        if(u==null||!Boolean.TRUE.equals(u.getAtivo())||!u.getPapeis().contains(com.obraslink.model.PapelUsuario.CLIENTE)||perfil.atual()!=com.obraslink.model.PapelUsuario.CLIENTE)return java.util.Set.of();
        var cliente=clientes.findByUsuarioId(u.getId()).orElse(null);
        if(cliente==null)return java.util.Set.of();
        return favoritos.findByClienteIdOrderByCriadoEmDesc(cliente.getId()).stream().map(com.obraslink.model.Favorito::getPrestadorId).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
    @Transactional(readOnly=true) public boolean ehFavorito(Long usuarioId,Long prestadorId){var cliente=clientes.findByUsuarioId(usuarioId).orElse(null);return cliente!=null&&favoritos.existsByClienteIdAndPrestadorId(cliente.getId(),prestadorId);}
    @Transactional public void alternarFavorito(Long prestadorId,boolean adicionar){
        var u=acesso.getUsuarioAtualOrThrow(); if(!Boolean.TRUE.equals(u.getAtivo())||!u.getPapeis().contains(com.obraslink.model.PapelUsuario.CLIENTE)||perfil.atual()!=com.obraslink.model.PapelUsuario.CLIENTE) throw new IllegalArgumentException("Ative o perfil de cliente para gerenciar favoritos.");
        Long clienteId=clientes.findByUsuarioId(u.getId()).orElseThrow(()->new IllegalArgumentException("Cadastro de cliente não encontrado.")).getId();
        if(!prestadores.findById(prestadorId).filter(p->Boolean.TRUE.equals(p.getAtivo())).isPresent()) throw new IllegalArgumentException("Profissional indisponível.");
        if(adicionar){if(!favoritos.existsByClienteIdAndPrestadorId(clienteId,prestadorId)) favoritos.save(new com.obraslink.model.Favorito(clienteId,prestadorId));} else favoritos.deleteByClienteIdAndPrestadorId(clienteId,prestadorId);
    }

    // ---------- Agenda do mês atual ----------

    /** Um dia do mês, com o status marcado pelo prestador (vazio = sem informação). */
    public record DiaAgenda(LocalDate data, AgendaDia.Status status) {}

    /** Agenda do mês atual pronta para exibir: rótulo do mês, todos os dias (com ou sem status) e o horário comercial. */
    public record AgendaMes(String mesLabel, List<DiaAgenda> dias, LocalTime horarioInicio, LocalTime horarioFim) {}

    private static final DateTimeFormatter MES_LABEL = DateTimeFormatter.ofPattern("MMMM 'de' yyyy", new Locale("pt", "BR"));

    private String capitalizarMes(YearMonth mes) {
        String texto = mes.format(MES_LABEL);
        return texto.isEmpty() ? texto : Character.toUpperCase(texto.charAt(0)) + texto.substring(1);
    }

    /** Todos os dias do mês atual, com o status que o prestador marcou (null quando não informado). */
    @Transactional(readOnly=true)
    public List<DiaAgenda> agendaDoMesAtual(Long prestadorId) {
        YearMonth mes = YearMonth.now();
        LocalDate inicio = mes.atDay(1), fim = mes.atEndOfMonth();
        Map<LocalDate, AgendaDia.Status> marcados = agendaDias.findByPrestadorIdAndDataBetweenOrderByDataAsc(prestadorId, inicio, fim)
                .stream().collect(Collectors.toMap(AgendaDia::getData, AgendaDia::getStatus));
        List<DiaAgenda> dias = new ArrayList<>();
        for (LocalDate d = inicio; !d.isAfter(fim); d = d.plusDays(1)) dias.add(new DiaAgenda(d, marcados.get(d)));
        return dias;
    }

    /** Agenda pública de um prestador (perfil visto pelo cliente): mês atual + horário comercial. */
    @Transactional(readOnly=true)
    public AgendaMes agendaPublica(Prestador prestador) {
        return new AgendaMes(capitalizarMes(YearMonth.now()), agendaDoMesAtual(prestador.getId()),
                prestador.getHorarioComercialInicio(), prestador.getHorarioComercialFim());
    }

    private Prestador prestadorAtual() {
        var u = acesso.getUsuarioAtualOrThrow();
        if (!Boolean.TRUE.equals(u.getAtivo()) || !u.getPapeis().contains(PapelUsuario.PRESTADOR) || perfil.atual() != PapelUsuario.PRESTADOR) {
            throw new IllegalArgumentException("Ative o perfil de prestador para gerenciar sua agenda.");
        }
        return prestadores.findByUsuarioId(u.getId()).orElseThrow(() -> new IllegalArgumentException("Cadastro profissional não encontrado."));
    }

    /** Agenda do prestador logado, para a tela de edição. */
    @Transactional(readOnly=true)
    public AgendaMes minhaAgendaMesAtual() {
        Prestador prestador = prestadorAtual();
        return new AgendaMes(capitalizarMes(YearMonth.now()), agendaDoMesAtual(prestador.getId()),
                prestador.getHorarioComercialInicio(), prestador.getHorarioComercialFim());
    }

    /** Substitui, de uma vez, o horário comercial e os status dos dias do mês atual marcados pelo prestador. */
    @Transactional
    public void salvarAgenda(LocalTime horarioInicio, LocalTime horarioFim, List<AgendaDia> itens) {
        Prestador prestador = prestadorAtual();
        if ((horarioInicio == null) != (horarioFim == null)) {
            throw new IllegalArgumentException("Informe o início e o fim do horário comercial, ou deixe os dois em branco.");
        }
        if (horarioInicio != null && !horarioInicio.isBefore(horarioFim)) {
            throw new IllegalArgumentException("O horário comercial deve começar antes de terminar.");
        }
        prestador.setHorarioComercialInicio(horarioInicio);
        prestador.setHorarioComercialFim(horarioFim);
        prestadores.save(prestador);

        YearMonth mes = YearMonth.now();
        LocalDate inicioMes = mes.atDay(1), fimMes = mes.atEndOfMonth();
        List<AgendaDia> validos = (itens == null ? java.util.List.<AgendaDia>of() : itens).stream()
                .filter(i -> i != null && i.getStatus() != null && i.getStatus() != AgendaDia.Status.SEM_INFORMACAO && i.getData() != null
                        && !i.getData().isBefore(inicioMes) && !i.getData().isAfter(fimMes))
                .peek(i -> i.setPrestadorId(prestador.getId()))
                .toList();
        agendaDias.deleteByPrestadorIdAndDataBetween(prestador.getId(), inicioMes, fimMes);
        // Sem o flush, o Hibernate agrupa todos os inserts antes dos deletes (ordem fixa da action queue)
        // e a chave única (prestador_id, data) seria violada pelos dias que já estavam marcados.
        agendaDias.flush();
        agendaDias.saveAll(validos);
    }
}
