package com.obraslink.controller;

import com.obraslink.dto.DenunciaForm;
import com.obraslink.model.Denuncia;
import com.obraslink.service.DenunciaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Denúncia de um participante contra o outro num atendimento (ver {@link DenunciaService}). */
@Controller
@RequiredArgsConstructor
@RequestMapping("/atendimentos/{id}/denunciar")
public class DenunciaController {
    private final DenunciaService service;

    @GetMapping
    public String formulario(@PathVariable Long id, Model model, RedirectAttributes redirect) {
        try {
            var detalhes = service.consultar(id);
            model.addAttribute("atendimento", detalhes.atendimento());
            model.addAttribute("denuncia", detalhes.denuncia());
            model.addAttribute("podeDenunciar", detalhes.podeDenunciar());
            if (!model.containsAttribute("denunciaForm")) {
                model.addAttribute("denunciaForm", new DenunciaForm());
            }
            model.addAttribute("motivos", Denuncia.Motivo.values());
            return "denuncias/novo";
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("mensagemErro", e.getMessage());
            return "redirect:/notificacoes";
        }
    }

    @PostMapping
    public String enviar(@PathVariable Long id, @ModelAttribute("denunciaForm") DenunciaForm form, RedirectAttributes redirect) {
        try {
            service.denunciar(id, form);
            redirect.addFlashAttribute("mensagem", "Denúncia registrada. Nossa equipe vai analisar.");
            return "redirect:/notificacoes";
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("mensagemErro", e.getMessage());
            redirect.addFlashAttribute("denunciaForm", form);
            return "redirect:/atendimentos/" + id + "/denunciar";
        }
    }
}
