package com.obraslink.service;

import com.obraslink.model.PapelUsuario;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service @RequiredArgsConstructor
public class PerfilAtivoService {
    public static final String SESSION_KEY="OBRALINK_PERFIL_ATIVO";
    private final AcessoService acesso;
    private final HttpSession session;
    public PapelUsuario atual() {
        var user=acesso.getUsuarioAtualOrThrow();
        Object saved=session.getAttribute(SESSION_KEY);
        if (saved instanceof String name) try {
            PapelUsuario role=PapelUsuario.valueOf(name);
            if ((role==PapelUsuario.CLIENTE || role==PapelUsuario.PRESTADOR) && user.getPapeis().contains(role)) return role;
        } catch (IllegalArgumentException ignored) { }
        PapelUsuario role=user.getPapeis().contains(PapelUsuario.CLIENTE)?PapelUsuario.CLIENTE:user.getPapeis().contains(PapelUsuario.PRESTADOR)?PapelUsuario.PRESTADOR:null;
        if(role!=null)session.setAttribute(SESSION_KEY,role.name());
        return role;
    }
    public void selecionar(PapelUsuario role) {
        if (role!=PapelUsuario.CLIENTE && role!=PapelUsuario.PRESTADOR) throw new IllegalArgumentException("Selecione Cliente ou Prestador.");
        var user=acesso.getUsuarioAtualOrThrow();
        if (!user.getPapeis().contains(role)) throw new IllegalArgumentException("Sua conta não possui esse perfil.");
        session.setAttribute(SESSION_KEY,role.name());
    }
}
