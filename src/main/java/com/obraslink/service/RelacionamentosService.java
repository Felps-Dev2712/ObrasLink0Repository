package com.obraslink.service;

import com.obraslink.repository.FavoritoRepository;
import com.obraslink.repository.DisponibilidadeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class RelacionamentosService {
    public record FavoritoView(Long id,String nome,String categoria){}
    private final AcessoService acesso;
    private final FavoritoRepository favoritos;
    private final DisponibilidadeRepository disponibilidades;
    private final com.obraslink.repository.ClienteRepository clientes;
    private final com.obraslink.repository.PrestadorRepository prestadores;
    private final PerfilAtivoService perfil;
    @Transactional(readOnly=true) public java.util.List<FavoritoView> favoritosDoAtual(){
        var u=acesso.getUsuarioAtualOrThrow(); if(!Boolean.TRUE.equals(u.getAtivo())||!u.getPapeis().contains(com.obraslink.model.PapelUsuario.CLIENTE)||perfil.atual()!=com.obraslink.model.PapelUsuario.CLIENTE) throw new IllegalArgumentException("Ative o perfil de cliente para consultar favoritos.");
        Long id=clientes.findByUsuarioId(u.getId()).orElseThrow(()->new IllegalArgumentException("Cadastro de cliente não encontrado.")).getId(); return favoritos.findPrestadoresFavoritos(id).stream().map(p->new FavoritoView(p.getId(),p.getNome(),p.getCategoria()==null?"Especialidade não informada":p.getCategoria().getNome())).toList();
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
    @Transactional(readOnly=true) public java.util.List<com.obraslink.model.Disponibilidade> disponibilidade(Long prestadorId){return disponibilidades.findByPrestadorIdOrderByDiaAscInicioAsc(prestadorId);}
    @Transactional(readOnly=true) public java.util.List<com.obraslink.model.Disponibilidade> disponibilidadeAtual(){var u=acesso.getUsuarioAtualOrThrow();if(!u.getPapeis().contains(com.obraslink.model.PapelUsuario.PRESTADOR)||perfil.atual()!=com.obraslink.model.PapelUsuario.PRESTADOR)throw new IllegalArgumentException("Ative o perfil de prestador para gerenciar disponibilidade.");var p=prestadores.findByUsuarioId(u.getId()).orElseThrow(()->new IllegalArgumentException("Cadastro profissional não encontrado."));return disponibilidade(p.getId());}
    @Transactional public void substituirDisponibilidade(java.util.List<com.obraslink.model.Disponibilidade> itens){
        var u=acesso.getUsuarioAtualOrThrow(); if(!Boolean.TRUE.equals(u.getAtivo())||!u.getPapeis().contains(com.obraslink.model.PapelUsuario.PRESTADOR)||perfil.atual()!=com.obraslink.model.PapelUsuario.PRESTADOR) throw new IllegalArgumentException("Ative o perfil de prestador para gerenciar disponibilidade.");
        Long pid=prestadores.findByUsuarioId(u.getId()).orElseThrow(()->new IllegalArgumentException("Cadastro profissional não encontrado.")).getId();
        if(itens!=null&&itens.stream().filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.groupingBy(com.obraslink.model.Disponibilidade::getDia,java.util.stream.Collectors.counting())).values().stream().anyMatch(total->total>6)) throw new IllegalArgumentException("Informe até seis períodos por dia.");
        if(itens==null) itens=java.util.List.of();
        var sorted=itens.stream().peek(d->{if(d.getDia()==null||d.getInicio()==null||d.getFim()==null||!d.getInicio().isBefore(d.getFim())) throw new IllegalArgumentException("Confira os dias e horários: o início deve ser anterior ao fim.");d.setPrestadorId(pid);}).sorted(java.util.Comparator.comparing(com.obraslink.model.Disponibilidade::getDia).thenComparing(com.obraslink.model.Disponibilidade::getInicio)).toList();
        for(int i=1;i<sorted.size();i++){var a=sorted.get(i-1);var b=sorted.get(i);if(a.getDia()==b.getDia()&&b.getInicio().isBefore(a.getFim())) throw new IllegalArgumentException("Os períodos de um mesmo dia não podem se sobrepor.");}
        disponibilidades.deleteByPrestadorId(pid); disponibilidades.saveAll(sorted);
    }
}
