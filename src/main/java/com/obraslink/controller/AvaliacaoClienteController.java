package com.obraslink.controller;

import com.obraslink.dto.AvaliacaoClienteForm;
import com.obraslink.service.AvaliacaoClienteService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
@RequestMapping("/atendimentos/{id}/avaliacao-cliente")
public class AvaliacaoClienteController {
    private final AvaliacaoClienteService service;

    @GetMapping
    public String detalhes(@PathVariable Long id, Model model, RedirectAttributes redirect) {
        try {
            var detalhes = service.consultar(id);
            model.addAttribute("atendimento", detalhes.atendimento());
            model.addAttribute("avaliacao", detalhes.avaliacao());
            model.addAttribute("podeAvaliar", detalhes.podeAvaliar());
            if (!model.containsAttribute("avaliacaoForm")) {
                model.addAttribute("avaliacaoForm", new AvaliacaoClienteForm());
            }
            return "avaliacoes/cliente";
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("mensagemErro", e.getMessage());
            return "redirect:/notificacoes";
        }
    }

    @PostMapping
    public String avaliar(@PathVariable Long id, @ModelAttribute("avaliacaoForm") AvaliacaoClienteForm form,
                          org.springframework.validation.BindingResult binding, RedirectAttributes redirect) {
        try {
            if (binding.hasErrors()) throw new IllegalArgumentException("Informe notas válidas entre 1 e 5.");
            service.avaliar(id, form);
            redirect.addFlashAttribute("mensagem", "Avaliação enviada ao cliente.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("mensagemErro", e.getMessage());
            redirect.addFlashAttribute("avaliacaoForm", form);
        }
        return "redirect:/atendimentos/" + id + "/avaliacao-cliente";
    }
}
