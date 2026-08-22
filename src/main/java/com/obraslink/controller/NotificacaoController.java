package com.obraslink.controller;

import com.obraslink.model.Usuario;
import com.obraslink.service.AcessoService;
import com.obraslink.service.NotificacaoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class NotificacaoController {

    private final AcessoService acessoService;
    private final NotificacaoService notificacaoService;

    @GetMapping("/notificacoes")
    public String listar(Model model) {
        Usuario usuario = acessoService.getUsuarioAtualOrThrow();
        model.addAttribute("notificacoes", notificacaoService.listarEMarcarComoLidas(usuario.getId()));
        return "notificacoes/lista";
    }
}
