package com.obraslink.controller;

import com.obraslink.model.Cliente;
import com.obraslink.model.PapelUsuario;
import com.obraslink.model.Usuario;
import com.obraslink.service.AcessoService;
import com.obraslink.service.ClienteService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/clientes")
@RequiredArgsConstructor
public class ClienteController {

    private final ClienteService clienteService;
    private final AcessoService acessoService;

    @GetMapping
    public String list(Model model) {
        Usuario usuario = acessoService.getUsuarioAtualOrThrow();
        boolean admin = acessoService.isAdmin(usuario);
        boolean prestador = usuario.getPapeis().contains(PapelUsuario.PRESTADOR);

        List<Cliente> clientes;
        if (admin || prestador) {
            clientes = clienteService.findAll();
        } else {
            clientes = clienteService.findByUsuarioId(usuario.getId())
                    .map(List::of)
                    .orElseGet(List::of);
        }

        model.addAttribute("clientes", clientes);
        model.addAttribute("isAdmin", admin);
        return "clientes/lista";
    }

    @GetMapping("/novo")
    public String createForm(Model model, RedirectAttributes redirectAttributes) {
        if (!acessoService.isAdmin()) {
            redirectAttributes.addFlashAttribute("mensagemErro", "Apenas administradores podem criar clientes por esta tela.");
            return "redirect:/clientes";
        }

        model.addAttribute("cliente", new Cliente());
        model.addAttribute("isAdmin", true);
        return "clientes/form";
    }

    @PostMapping
    public String save(@ModelAttribute Cliente cliente, RedirectAttributes redirectAttributes) {
        if (!acessoService.isAdmin()) {
            redirectAttributes.addFlashAttribute("mensagemErro", "Apenas administradores podem criar clientes por esta tela.");
            return "redirect:/clientes";
        }

        clienteService.save(cliente);
        redirectAttributes.addFlashAttribute("mensagem", "Cliente salvo com sucesso!");
        return "redirect:/clientes";
    }

    @GetMapping("/{id}/editar")
    public String editForm(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        if (!acessoService.podeGerenciarCliente(id)) {
            redirectAttributes.addFlashAttribute("mensagemErro", "Voce nao tem permissao para editar este cliente.");
            return "redirect:/clientes";
        }

        return clienteService.findById(id).map(cliente -> {
            model.addAttribute("cliente", cliente);
            model.addAttribute("isAdmin", acessoService.isAdmin());
            return "clientes/form";
        }).orElse("redirect:/clientes");
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id,
                         @ModelAttribute Cliente cliente,
                         RedirectAttributes redirectAttributes) {
        if (!acessoService.podeGerenciarCliente(id)) {
            redirectAttributes.addFlashAttribute("mensagemErro", "Voce nao tem permissao para editar este cliente.");
            return "redirect:/clientes";
        }

        Cliente existente = clienteService.findById(id).orElse(null);
        if (existente == null) {
            redirectAttributes.addFlashAttribute("mensagemErro", "Cliente nao encontrado.");
            return "redirect:/clientes";
        }

        cliente.setId(id);
        cliente.setUsuarioId(existente.getUsuarioId());
        if (!acessoService.isAdmin()) {
            cliente.setEmail(existente.getEmail());
        }

        clienteService.save(cliente);
        redirectAttributes.addFlashAttribute("mensagem", "Cliente atualizado com sucesso!");
        return "redirect:/clientes";
    }

    @PostMapping("/{id}/excluir")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        if (!acessoService.podeGerenciarCliente(id)) {
            redirectAttributes.addFlashAttribute("mensagemErro", "Voce nao tem permissao para excluir este cliente.");
            return "redirect:/clientes";
        }

        clienteService.delete(id);
        redirectAttributes.addFlashAttribute("mensagem", "Cliente excluido com sucesso!");
        return "redirect:/clientes";
    }
}
