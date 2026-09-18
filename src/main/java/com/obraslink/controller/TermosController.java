package com.obraslink.controller;

import com.obraslink.service.AcessoService;
import com.obraslink.service.TermosService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class TermosController {
    private final TermosService termosService;
    private final AcessoService acessoService;

    @GetMapping("/termos")
    public String termos() {
        return "legal/" + TermosService.VERSAO_ATUAL + "/termos";
    }

    @GetMapping("/privacidade")
    public String privacidade() {
        return "legal/" + TermosService.VERSAO_ATUAL + "/privacidade";
    }

    @GetMapping("/termos/aceitar")
    public String formulario(Model model) {
        if (termosService.aceitouVersaoAtual(acessoService.getUsuarioAtualOrThrow().getId())) {
            return "redirect:/";
        }
        model.addAttribute("versao", TermosService.VERSAO_ATUAL);
        return "auth/aceitar-termos";
    }

    @PostMapping("/termos/aceitar")
    public String aceitar(@RequestParam(defaultValue = "") String versao,
                          @RequestParam(defaultValue = "false") boolean aceitouTermos,
                          @RequestParam(defaultValue = "false") boolean cientePrivacidade,
                          RedirectAttributes attributes) {
        try {
            termosService.aceitar(versao, aceitouTermos, cientePrivacidade);
            return "redirect:/";
        } catch (IllegalArgumentException ex) {
            attributes.addFlashAttribute("erro", ex.getMessage());
            return "redirect:/termos/aceitar";
        }
    }
}
