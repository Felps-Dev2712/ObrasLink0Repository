package com.obraslink.controller;

import com.obraslink.model.Prestador;
import com.obraslink.service.CategoriaService;
import com.obraslink.service.PrestadorService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/prestadores")
@RequiredArgsConstructor
public class PrestadorController {

    private final PrestadorService prestadorService;
    private final CategoriaService categoriaService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("prestadores", prestadorService.findAllActive());
        return "prestadores/lista";
    }

    @GetMapping("/novo")
    public String createForm(Model model) {
        model.addAttribute("prestador", new Prestador());
        model.addAttribute("categorias", categoriaService.findAll());
        return "prestadores/form";
    }

    @PostMapping
    public String save(@ModelAttribute Prestador prestador, RedirectAttributes redirectAttributes) {
        prestadorService.save(prestador);
        redirectAttributes.addFlashAttribute("mensagem", "Prestador salvo com sucesso!");
        return "redirect:/prestadores";
    }

    @GetMapping("/{id}")
    public String view(@PathVariable Long id, Model model) {
        return prestadorService.findById(id).map(prestador -> {
            model.addAttribute("prestador", prestador);
            return "prestadores/detalhes";
        }).orElse("redirect:/prestadores");
    }

    @GetMapping("/{id}/editar")
    public String editForm(@PathVariable Long id, Model model) {
        return prestadorService.findById(id).map(prestador -> {
            model.addAttribute("prestador", prestador);
            model.addAttribute("categorias", categoriaService.findAll());
            return "prestadores/form";
        }).orElse("redirect:/prestadores");
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @ModelAttribute Prestador prestador, RedirectAttributes redirectAttributes) {
        prestador.setId(id);
        prestadorService.save(prestador);
        redirectAttributes.addFlashAttribute("mensagem", "Prestador atualizado com sucesso!");
        return "redirect:/prestadores";
    }

    @PostMapping("/{id}/excluir")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        prestadorService.delete(id);
        redirectAttributes.addFlashAttribute("mensagem", "Prestador excluído com sucesso!");
        return "redirect:/prestadores";
    }
}
