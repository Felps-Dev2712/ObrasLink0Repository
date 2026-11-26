package com.obraslink.controller;

import com.obraslink.model.Plano;
import com.obraslink.service.PlanoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Tela de planos de assinatura do prestador: comparação dos planos e troca (simulada) do plano atual. */
@Controller
@RequiredArgsConstructor
public class PlanoController {

    private final PlanoService planoService;

    @GetMapping("/prestador/planos")
    public String planos(Model model, RedirectAttributes redirectAttributes) {
        try {
            model.addAttribute("planos", planoService.todos());
            model.addAttribute("planoAtual", planoService.prestadorAtual().getPlano());
            return "planos/index";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("mensagemErro", e.getMessage());
            return "redirect:/clientes";
        }
    }

    @PostMapping("/prestador/planos/assinar")
    public String assinar(@RequestParam Plano plano, RedirectAttributes redirectAttributes) {
        try {
            Plano assinado = planoService.assinar(plano);
            redirectAttributes.addFlashAttribute("mensagem", "Plano " + assinado.getNome() + " ativado com sucesso!");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("mensagemErro", e.getMessage());
        }
        return "redirect:/prestador/planos";
    }
}
