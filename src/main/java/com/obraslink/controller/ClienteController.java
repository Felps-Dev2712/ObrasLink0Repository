package com.obraslink.controller;

import com.obraslink.model.Cliente;
import com.obraslink.model.Usuario;
import com.obraslink.service.AcessoService;
import com.obraslink.service.CategoriaService;
import com.obraslink.service.ClienteService;
import com.obraslink.service.ContaService;
import com.obraslink.service.PrestadorService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/clientes")
@RequiredArgsConstructor
public class ClienteController {

    private final ClienteService clienteService;
    private final PrestadorService prestadorService;
    private final CategoriaService categoriaService;
    private final AcessoService acessoService;
    private final ContaService contaService;

    @PostMapping("/minha-conta/excluir")
    public String deleteOwnAccount(HttpServletRequest request,
                                   HttpServletResponse response,
                                   Authentication authentication,
                                   RedirectAttributes redirectAttributes) {
        try {
            contaService.excluirMinhaConta();
        } catch (DataAccessException ex) {
            redirectAttributes.addFlashAttribute("mensagemErro",
                    "Não foi possível excluir sua conta. Tente novamente.");
            return "redirect:/clientes";
        }
        new SecurityContextLogoutHandler()
                .logout(request, response, authentication);
        return "redirect:/login?contaExcluida";
    }

    @GetMapping
    public String list(Model model) {
        Usuario usuario = acessoService.getUsuarioAtualOrThrow();
        model.addAttribute("cliente", clienteService.findByUsuarioId(usuario.getId()).orElse(null));
        model.addAttribute("prestador", prestadorService.findByUsuarioId(usuario.getId()).orElse(null));
        model.addAttribute("categorias", categoriaService.findAll());
        return "clientes/minha-conta";
    }

    @PostMapping("/minha-conta")
    public String updateOwnProfile(@ModelAttribute Cliente dados,
                                   RedirectAttributes redirectAttributes) {
        Usuario usuario = acessoService.getUsuarioAtualOrThrow();
        Cliente cliente = clienteService.findByUsuarioId(usuario.getId()).orElse(null);
        if (cliente == null) {
            redirectAttributes.addFlashAttribute("mensagemErro", "Seu perfil de cliente não foi encontrado.");
            return "redirect:/clientes";
        }

        cliente.setNome(dados.getNome());
        cliente.setCpfCnpj(dados.getCpfCnpj());
        cliente.setTelefone(dados.getTelefone());
        clienteService.save(cliente);
        redirectAttributes.addFlashAttribute("mensagem", "Dados de cliente atualizados com sucesso!");
        return "redirect:/clientes";
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
