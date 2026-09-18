package com.obraslink.controller;

import com.obraslink.model.Usuario;
import com.obraslink.service.AcessoService;
import com.obraslink.service.NotificacaoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import com.obraslink.service.AtendimentoService;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class NotificacaoController {

    private final AcessoService acessoService;
    private final NotificacaoService notificacaoService;

    private final AtendimentoService atendimentoService;

    @GetMapping("/notificacoes")
    public String listar(Model model) {
        Usuario usuario = acessoService.getUsuarioAtualOrThrow();
        model.addAttribute("notificacoes", notificacaoService.listarEMarcarComoLidas(usuario.getId()));
        model.addAttribute("atendimentos", atendimentoService.listar());
        model.addAttribute("usuarioAtualId", usuario.getId());
        return "notificacoes/lista";
    }
    @GetMapping("/notificacoes/contador")
    @ResponseBody
    public Map<String, Long> contador() {
        return Map.of("total", notificacaoService.contarNaoLidas(acessoService.getUsuarioAtualOrThrow().getId()));
    }

    @PostMapping("/prestadores/{id}/contato")
    public String contatar(@PathVariable Long id, RedirectAttributes attributes) {
        try {
            return "redirect:" + atendimentoService.contatar(id);
        } catch (IllegalArgumentException ex) {
            attributes.addFlashAttribute("mensagemErro", ex.getMessage());
            return "redirect:/prestadores/" + id;
        }
    }

    @PostMapping("/notificacoes/atendimentos/{id}/confirmar")
    public String confirmar(@PathVariable Long id, RedirectAttributes attributes) {
        return atualizar(id, false, attributes);
    }

    @PostMapping("/notificacoes/atendimentos/{id}/concluir")
    public String concluir(@PathVariable Long id, RedirectAttributes attributes) {
        return atualizar(id, true, attributes);
    }

    private String atualizar(Long id, boolean concluir, RedirectAttributes attributes) {
        try {
            atendimentoService.atualizar(id, concluir);
            attributes.addFlashAttribute("mensagem", concluir ? "Serviço concluído!" : "Contratação confirmada!");
        } catch (IllegalArgumentException ex) {
            attributes.addFlashAttribute("mensagemErro", ex.getMessage());
        }
        return "redirect:/notificacoes";
    }
}
