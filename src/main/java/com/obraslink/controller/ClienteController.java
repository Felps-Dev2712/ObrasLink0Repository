package com.obraslink.controller;

import com.obraslink.model.Cliente;
import com.obraslink.service.ClienteService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/clientes")
@RequiredArgsConstructor
public class ClienteController {

    private final ClienteService clienteService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("clientes", clienteService.findAll());
        return "clientes/lista";
    }

    @GetMapping("/novo")
    public String createForm(Model model) {
        model.addAttribute("cliente", new Cliente());
        return "clientes/form";
    }

    @PostMapping
    public String save(@ModelAttribute Cliente cliente, RedirectAttributes redirectAttributes) {
        clienteService.save(cliente);
        redirectAttributes.addFlashAttribute("mensagem", "Cliente salvo com sucesso!");
        return "redirect:/clientes";
    }

    @GetMapping("/{id}/editar")
    public String editForm(@PathVariable Long id, Model model) {
        return clienteService.findById(id).map(cliente -> {
            model.addAttribute("cliente", cliente);
            return "clientes/form";
        }).orElse("redirect:/clientes");
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @ModelAttribute Cliente cliente, RedirectAttributes redirectAttributes) {
        cliente.setId(id);
        clienteService.save(cliente);
        redirectAttributes.addFlashAttribute("mensagem", "Cliente atualizado com sucesso!");
        return "redirect:/clientes";
    }

    @PostMapping("/{id}/excluir")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        clienteService.delete(id);
        redirectAttributes.addFlashAttribute("mensagem", "Cliente excluído com sucesso!");
        return "redirect:/clientes";
    }
}
