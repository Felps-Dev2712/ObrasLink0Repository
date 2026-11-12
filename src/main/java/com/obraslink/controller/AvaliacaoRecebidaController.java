package com.obraslink.controller;

import com.obraslink.service.AvaliacaoPrestadorService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Avaliação que o CLIENTE fez do profissional em um atendimento (Qualidade, Prazo, Preço, Organização e Atendimento).
 * É separada da avaliação que o profissional faz do cliente (/atendimentos/{id}/avaliacao-cliente).
 */
@Controller
@RequiredArgsConstructor
public class AvaliacaoRecebidaController {
    private final AvaliacaoPrestadorService avaliacoes;

    @GetMapping("/atendimentos/{id}/avaliacao-profissional")
    public String detalhes(@PathVariable Long id, Model model, RedirectAttributes redirect) {
        try {
            var recebida = avaliacoes.consultarRecebida(id);
            model.addAttribute("atendimento", recebida.atendimento());
            model.addAttribute("avaliacao", recebida.avaliacao());
            model.addAttribute("media", recebida.media());
            model.addAttribute("souPrestador", recebida.souPrestador());
            return "avaliacoes/profissional";
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("mensagemErro", e.getMessage());
            return "redirect:/notificacoes";
        }
    }
}
