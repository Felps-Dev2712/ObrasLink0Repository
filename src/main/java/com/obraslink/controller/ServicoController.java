package com.obraslink.controller;

import com.obraslink.model.Servico;
import com.obraslink.service.CategoriaService;
import com.obraslink.service.PrestadorService;
import com.obraslink.service.ServicoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class ServicoController {

    private final ServicoService servicoService;
    private final PrestadorService prestadorService;
    private final CategoriaService categoriaService;

    @GetMapping("/vitrine")
    public String vitrine(@RequestParam(required = false) Long categoriaId, Model model) {
        model.addAttribute("servicos", servicoService.findByCategoria(categoriaId));
        model.addAttribute("categorias", categoriaService.findAll());
        model.addAttribute("categoriaAtiva", categoriaId);
        return "vitrine/index";
    }

    @GetMapping("/servicos")
    public String list(Model model) {
        model.addAttribute("servicos", servicoService.findAllAvailable());
        return "servicos/lista";
    }

    @GetMapping("/servicos/novo")
    public String createForm(Model model) {
        model.addAttribute("servico", new Servico());
        model.addAttribute("prestadores", prestadorService.findAllActive());
        model.addAttribute("categorias", categoriaService.findAll());
        return "servicos/form";
    }

    @PostMapping("/servicos")
    public String save(@ModelAttribute Servico servico, RedirectAttributes redirectAttributes) {
        servicoService.save(servico);
        redirectAttributes.addFlashAttribute("mensagem", "Serviço criado com sucesso!");
        return "redirect:/servicos";
    }

    @GetMapping("/servicos/{id}/editar")
    public String editForm(@PathVariable Long id, Model model) {
        return servicoService.findById(id).map(servico -> {
            model.addAttribute("servico", servico);
            model.addAttribute("prestadores", prestadorService.findAllActive());
            model.addAttribute("categorias", categoriaService.findAll());
            return "servicos/form";
        }).orElse("redirect:/servicos");
    }

    @PostMapping("/servicos/{id}")
    public String update(@PathVariable Long id, @ModelAttribute Servico servico, RedirectAttributes redirectAttributes) {
        servico.setId(id);
        servicoService.save(servico);
        redirectAttributes.addFlashAttribute("mensagem", "Serviço atualizado com sucesso!");
        return "redirect:/servicos";
    }

    @PostMapping("/servicos/{id}/excluir")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        servicoService.delete(id);
        redirectAttributes.addFlashAttribute("mensagem", "Serviço excluído com sucesso!");
        return "redirect:/servicos";
    }
}
