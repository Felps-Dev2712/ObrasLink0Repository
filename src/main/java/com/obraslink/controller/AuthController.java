package com.obraslink.controller;

import com.obraslink.dto.RegistroUsuarioForm;
import com.obraslink.dto.CadastroCertificacaoForm;
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
import org.springframework.dao.DataIntegrityViolationException;

@Controller
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final CategoriaService categoriaService;
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    private com.obraslink.service.IndicadoresPlataformaService indicadores;

    @GetMapping("/login")
    public String login(Model model) {
        if(indicadores!=null)model.addAttribute("indicadores",indicadores.carregar());
        return "auth/login";
    }

    @GetMapping("/recuperar-senha")
    public String recuperarSenhaForm() {
        return "auth/recuperar-senha";
    }

    @PostMapping("/recuperar-senha")
    public String solicitarRecuperacaoSenha(@RequestParam String email,
                                            RedirectAttributes redirectAttributes) {
        // Mantém a mesma resposta para qualquer e-mail e evita expor contas cadastradas.
        redirectAttributes.addFlashAttribute("mensagem",
                "Se houver uma conta associada a este e-mail, você receberá as instruções de recuperação.");
        return "redirect:/recuperar-senha";
    }

    @GetMapping("/register")
    public String registerForm(@RequestParam(required = false) String perfil, Model model) {
        if (!model.containsAttribute("form")) {
            RegistroUsuarioForm form = new RegistroUsuarioForm();
            form.getCertificacoes().add(new CadastroCertificacaoForm());
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
            boolean enviouCertificacao = Boolean.TRUE.equals(form.getCadastroPrestador())
                    && form.getCertificacoes() != null
                    && form.getCertificacoes().stream().anyMatch(certificacao -> !certificacao.isVazio());
            redirectAttributes.addFlashAttribute("mensagem", enviouCertificacao
                    ? "Conta criada. Suas certificações foram enviadas para análise. Faça login para continuar."
                    : "Conta criada com sucesso. Faça login para continuar.");
            return "redirect:/login";
        } catch (IllegalArgumentException ex) {
            model.addAttribute("erro", ex.getMessage());
            model.addAttribute("categorias", categoriaService.findAll());
            if (form.getCertificacoes() == null || form.getCertificacoes().isEmpty()) {
                form.setCertificacoes(new java.util.ArrayList<>(java.util.List.of(new CadastroCertificacaoForm())));
            }
            return "auth/register";
        } catch (DataIntegrityViolationException ex) {
            model.addAttribute("erro", "Não foi possível cadastrar: confira se e-mail ou CPF/CNPJ já estão em uso.");
            model.addAttribute("categorias", categoriaService.findAll());
            if (form.getCertificacoes() == null || form.getCertificacoes().isEmpty()) {
                form.setCertificacoes(new java.util.ArrayList<>(java.util.List.of(new CadastroCertificacaoForm())));
            }
            return "auth/register";
        }
    }
}
