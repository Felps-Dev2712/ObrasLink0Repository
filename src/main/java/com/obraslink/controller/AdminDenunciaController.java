package com.obraslink.controller;

import com.obraslink.model.Denuncia;
import com.obraslink.service.DenunciaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Análise administrativa das denúncias pendentes (ver {@link DenunciaService#analisar}). */
@Controller
@RequiredArgsConstructor
public class AdminDenunciaController {
    private final DenunciaService service;

    @GetMapping("/admin/denuncias")
    public String pendentes(Model model) {
        model.addAttribute("denuncias", service.pendentes());
        return "admin/denuncias";
    }

    @PostMapping("/admin/denuncias/{id}/analise")
    public String analisar(@PathVariable Long id, @RequestParam Denuncia.Estado estado,
                           @RequestParam(required = false) String observacao, RedirectAttributes attrs) {
        try {
            service.analisar(id, estado, observacao);
            attrs.addFlashAttribute("mensagem", "Denúncia analisada.");
        } catch (IllegalArgumentException e) {
            attrs.addFlashAttribute("mensagemErro", e.getMessage());
        }
        return "redirect:/admin/denuncias";
    }
}
