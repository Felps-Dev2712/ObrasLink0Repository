package com.obraslink.controller;

import com.obraslink.dto.RegistroUsuarioForm;
import com.obraslink.service.CategoriaService;
import com.obraslink.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final CategoriaService categoriaService;

    @GetMapping("/login")
    public String login() {
        return "auth/login";
    }

    @GetMapping("/register")
    public String registerForm(@RequestParam(required = false) String perfil, Model model) {
        if (!model.containsAttribute("form")) {
            RegistroUsuarioForm form = new RegistroUsuarioForm();
            if ("cliente".equalsIgnoreCase(perfil)) {
                form.setCadastroCliente(true);
            } else if ("prestador".equalsIgnoreCase(perfil)) {
                form.setCadastroPrestador(true);
            } else if ("ambos".equalsIgnoreCase(perfil)) {
                form.setCadastroCliente(true);
                form.setCadastroPrestador(true);
            }
            model.addAttribute("form", form);
        }
        model.addAttribute("categorias", categoriaService.findAll());
        return "auth/register";
    }

    @PostMapping("/register")
    public String register(@ModelAttribute("form") RegistroUsuarioForm form,
                           Model model,
                           RedirectAttributes redirectAttributes) {
        try {
            authService.registrar(form);
            redirectAttributes.addFlashAttribute("mensagem", "Conta criada com sucesso. Faça login para continuar.");
            return "redirect:/login";
        } catch (IllegalArgumentException ex) {
            model.addAttribute("erro", ex.getMessage());
            model.addAttribute("categorias", categoriaService.findAll());
            return "auth/register";
        }
    }
}
