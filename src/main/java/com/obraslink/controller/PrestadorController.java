package com.obraslink.controller;

import com.obraslink.model.Prestador;
import com.obraslink.model.Usuario;
import com.obraslink.service.AcessoService;
import com.obraslink.service.CategoriaService;
import com.obraslink.service.PrestadorService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Optional;

@Controller
@RequestMapping("/prestadores")
@RequiredArgsConstructor
public class PrestadorController {

    private final PrestadorService prestadorService;
    private final CategoriaService categoriaService;
    private final AcessoService acessoService;

    @GetMapping
    public String list(Model model) {
        Optional<Usuario> usuarioOpt = acessoService.getUsuarioAtual();
        boolean admin = usuarioOpt.map(acessoService::isAdmin).orElse(false);
        Long usuarioAtualId = usuarioOpt.map(Usuario::getId).orElse(null);

        model.addAttribute("prestadores", prestadorService.findAllActive());
        model.addAttribute("isAdmin", admin);
        model.addAttribute("usuarioAtualId", usuarioAtualId);
        return "prestadores/lista";
    }

    @GetMapping("/novo")
    public String createForm(Model model, RedirectAttributes redirectAttributes) {
        if (!acessoService.isAdmin()) {
            redirectAttributes.addFlashAttribute("mensagemErro", "Apenas administradores podem criar prestadores por esta tela.");
            return "redirect:/prestadores";
        }

        model.addAttribute("prestador", new Prestador());
        model.addAttribute("categorias", categoriaService.findAll());
        model.addAttribute("isAdmin", true);
        return "prestadores/form";
    }

    @PostMapping
    public String save(@ModelAttribute Prestador prestador, RedirectAttributes redirectAttributes) {
        if (!acessoService.isAdmin()) {
            redirectAttributes.addFlashAttribute("mensagemErro", "Apenas administradores podem criar prestadores por esta tela.");
            return "redirect:/prestadores";
        }

        prestadorService.save(prestador);
        redirectAttributes.addFlashAttribute("mensagem", "Prestador salvo com sucesso!");
        return "redirect:/prestadores";
    }

    @GetMapping("/{id}")
    public String view(@PathVariable Long id, Model model) {
        return prestadorService.findById(id).map(prestador -> {
            model.addAttribute("prestador", prestador);
            model.addAttribute("podeGerenciarPrestador", acessoService.podeGerenciarPrestador(prestador.getId()));
            return "prestadores/detalhes";
        }).orElse("redirect:/prestadores");
    }

    @GetMapping("/{id}/editar")
    public String editForm(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        if (!acessoService.podeGerenciarPrestador(id)) {
            redirectAttributes.addFlashAttribute("mensagemErro", "Voce nao tem permissao para editar este prestador.");
            return "redirect:/prestadores";
        }

        return prestadorService.findById(id).map(prestador -> {
            model.addAttribute("prestador", prestador);
            model.addAttribute("categorias", categoriaService.findAll());
            model.addAttribute("isAdmin", acessoService.isAdmin());
            return "prestadores/form";
        }).orElse("redirect:/prestadores");
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id,
                         @ModelAttribute Prestador prestador,
                         RedirectAttributes redirectAttributes) {
        if (!acessoService.podeGerenciarPrestador(id)) {
            redirectAttributes.addFlashAttribute("mensagemErro", "Voce nao tem permissao para editar este prestador.");
            return "redirect:/prestadores";
        }

        Prestador existente = prestadorService.findById(id).orElse(null);
        if (existente == null) {
            redirectAttributes.addFlashAttribute("mensagemErro", "Prestador nao encontrado.");
            return "redirect:/prestadores";
        }

        prestador.setId(id);
        prestador.setUsuarioId(existente.getUsuarioId());
        if (!acessoService.isAdmin()) {
            prestador.setEmail(existente.getEmail());
        }

        prestadorService.save(prestador);
        redirectAttributes.addFlashAttribute("mensagem", "Prestador atualizado com sucesso!");
        return "redirect:/prestadores";
    }

    @PostMapping("/{id}/excluir")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        if (!acessoService.podeGerenciarPrestador(id)) {
            redirectAttributes.addFlashAttribute("mensagemErro", "Voce nao tem permissao para excluir este prestador.");
            return "redirect:/prestadores";
        }

        prestadorService.delete(id);
        redirectAttributes.addFlashAttribute("mensagem", "Prestador excluido com sucesso!");
        return "redirect:/prestadores";
    }
}
