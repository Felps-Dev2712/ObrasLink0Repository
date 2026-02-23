package com.obraslink.controller;

import com.obraslink.model.PapelUsuario;
import com.obraslink.model.Prestador;
import com.obraslink.model.Servico;
import com.obraslink.model.Usuario;
import com.obraslink.service.AcessoService;
import com.obraslink.service.CategoriaService;
import com.obraslink.service.PrestadorService;
import com.obraslink.service.ServicoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Collections;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class ServicoController {

    private final ServicoService servicoService;
    private final PrestadorService prestadorService;
    private final CategoriaService categoriaService;
    private final AcessoService acessoService;

    @GetMapping("/vitrine")
    public String vitrine(@RequestParam(required = false) Long categoriaId, Model model) {
        model.addAttribute("servicos", servicoService.findByCategoria(categoriaId));
        model.addAttribute("categorias", categoriaService.findAll());
        model.addAttribute("categoriaAtiva", categoriaId);
        return "vitrine/index";
    }

    @GetMapping("/servicos")
    public String list(Model model) {
        Usuario usuario = acessoService.getUsuarioAtualOrThrow();
        boolean admin = acessoService.isAdmin(usuario);
        boolean cliente = usuario.getPapeis().contains(PapelUsuario.CLIENTE);
        boolean prestador = usuario.getPapeis().contains(PapelUsuario.PRESTADOR);

        Long prestadorAtualId = null;
        List<Servico> servicos;
        Prestador prestadorAtual = null;
        if (prestador) {
            prestadorAtual = prestadorService.findByUsuarioId(usuario.getId()).orElse(null);
            if (prestadorAtual != null) {
                prestadorAtualId = prestadorAtual.getId();
            }
        }

        if (admin) {
            servicos = servicoService.findAll();
        } else if (prestador && !cliente) {
            if (prestadorAtual == null) {
                servicos = Collections.emptyList();
            } else {
                servicos = servicoService.findByPrestador(prestadorAtualId);
            }
        } else {
            servicos = servicoService.findAllAvailable();
        }

        model.addAttribute("servicos", servicos);
        model.addAttribute("isAdmin", admin);
        model.addAttribute("prestadorAtualId", prestadorAtualId);
        model.addAttribute("podeGerenciarServicos", admin || prestador);
        return "servicos/lista";
    }

    @GetMapping("/servicos/novo")
    public String createForm(Model model, RedirectAttributes redirectAttributes) {
        boolean admin = acessoService.isAdmin();
        boolean prestador = acessoService.hasPapel(PapelUsuario.PRESTADOR);
        if (!admin && !prestador) {
            redirectAttributes.addFlashAttribute("mensagemErro", "Voce nao tem permissao para criar servicos.");
            return "redirect:/servicos";
        }

        model.addAttribute("servico", new Servico());

        if (admin) {
            model.addAttribute("prestadores", prestadorService.findAllActive());
        } else {
            Usuario usuario = acessoService.getUsuarioAtualOrThrow();
            Prestador prestadorAtual = prestadorService.findByUsuarioId(usuario.getId()).orElse(null);
            if (prestadorAtual == null) {
                redirectAttributes.addFlashAttribute("mensagemErro", "Seu usuario nao possui cadastro de prestador.");
                return "redirect:/servicos";
            }
            model.addAttribute("prestadores", List.of(prestadorAtual));
            model.addAttribute("prestadorAtual", prestadorAtual);
        }

        model.addAttribute("categorias", categoriaService.findAll());
        model.addAttribute("isAdmin", admin);
        return "servicos/form";
    }

    @PostMapping("/servicos")
    public String save(@ModelAttribute Servico servico, RedirectAttributes redirectAttributes) {
        boolean admin = acessoService.isAdmin();
        boolean prestador = acessoService.hasPapel(PapelUsuario.PRESTADOR);
        if (!admin && !prestador) {
            redirectAttributes.addFlashAttribute("mensagemErro", "Voce nao tem permissao para criar servicos.");
            return "redirect:/servicos";
        }

        if (!admin) {
            Usuario usuario = acessoService.getUsuarioAtualOrThrow();
            Prestador prestadorAtual = prestadorService.findByUsuarioId(usuario.getId()).orElse(null);
            if (prestadorAtual == null) {
                redirectAttributes.addFlashAttribute("mensagemErro", "Seu usuario nao possui cadastro de prestador.");
                return "redirect:/servicos";
            }
            servico.setPrestador(prestadorAtual);
        }

        servicoService.save(servico);
        redirectAttributes.addFlashAttribute("mensagem", "Servico criado com sucesso!");
        return "redirect:/servicos";
    }

    @GetMapping("/servicos/{id}/editar")
    public String editForm(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        if (!acessoService.podeGerenciarServico(id)) {
            redirectAttributes.addFlashAttribute("mensagemErro", "Voce nao tem permissao para editar este servico.");
            return "redirect:/servicos";
        }

        boolean admin = acessoService.isAdmin();

        return servicoService.findById(id).map(servico -> {
            model.addAttribute("servico", servico);
            if (admin) {
                model.addAttribute("prestadores", prestadorService.findAllActive());
            } else {
                model.addAttribute("prestadores", List.of(servico.getPrestador()));
                model.addAttribute("prestadorAtual", servico.getPrestador());
            }
            model.addAttribute("categorias", categoriaService.findAll());
            model.addAttribute("isAdmin", admin);
            return "servicos/form";
        }).orElse("redirect:/servicos");
    }

    @PostMapping("/servicos/{id}")
    public String update(@PathVariable Long id,
                         @ModelAttribute Servico servico,
                         RedirectAttributes redirectAttributes) {
        if (!acessoService.podeGerenciarServico(id)) {
            redirectAttributes.addFlashAttribute("mensagemErro", "Voce nao tem permissao para editar este servico.");
            return "redirect:/servicos";
        }

        Servico existente = servicoService.findById(id).orElse(null);
        if (existente == null) {
            redirectAttributes.addFlashAttribute("mensagemErro", "Servico nao encontrado.");
            return "redirect:/servicos";
        }

        servico.setId(id);

        if (!acessoService.isAdmin()) {
            Usuario usuario = acessoService.getUsuarioAtualOrThrow();
            Prestador prestadorAtual = prestadorService.findByUsuarioId(usuario.getId()).orElse(null);
            if (prestadorAtual == null) {
                redirectAttributes.addFlashAttribute("mensagemErro", "Seu usuario nao possui cadastro de prestador.");
                return "redirect:/servicos";
            }
            servico.setPrestador(prestadorAtual);
        } else if (servico.getPrestador() == null) {
            servico.setPrestador(existente.getPrestador());
        }

        servicoService.save(servico);
        redirectAttributes.addFlashAttribute("mensagem", "Servico atualizado com sucesso!");
        return "redirect:/servicos";
    }

    @PostMapping("/servicos/{id}/excluir")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        if (!acessoService.podeGerenciarServico(id)) {
            redirectAttributes.addFlashAttribute("mensagemErro", "Voce nao tem permissao para excluir este servico.");
            return "redirect:/servicos";
        }

        servicoService.delete(id);
        redirectAttributes.addFlashAttribute("mensagem", "Servico excluido com sucesso!");
        return "redirect:/servicos";
    }
}
