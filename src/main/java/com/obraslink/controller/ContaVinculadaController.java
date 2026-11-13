package com.obraslink.controller;

import com.obraslink.security.ContasVinculadasCookie;
import com.obraslink.service.ContaVinculadaService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Trocar entre contas já usadas neste navegador, sem digitar a senha de novo (ver {@link ContaVinculadaService}). */
@Controller
@RequiredArgsConstructor
public class ContaVinculadaController {
    private final ContaVinculadaService contaVinculadaService;
    private final ContasVinculadasCookie cookie;
    private final UserDetailsService usuarioDetailsService;
    private final SecurityContextRepository securityContextRepository;

    @PostMapping("/contas/trocar")
    public String trocar(@RequestParam String token, HttpServletRequest request, HttpServletResponse response,
                         RedirectAttributes redirectAttributes) {
        var usuario = contaVinculadaService.autenticar(token);
        if (usuario.isEmpty()) {
            cookie.remover(request, response, token);
            redirectAttributes.addFlashAttribute("mensagemErro", "Essa conta não está mais disponível para troca rápida. Faça login de novo.");
            return "redirect:/login";
        }

        var detalhes = usuarioDetailsService.loadUserByUsername(usuario.get().getEmail());
        var autenticacao = new UsernamePasswordAuthenticationToken(detalhes, null, detalhes.getAuthorities());
        request.getSession(); // garante sessão antes de trocar o id
        request.changeSessionId();
        SecurityContext contexto = SecurityContextHolder.createEmptyContext();
        contexto.setAuthentication(autenticacao);
        SecurityContextHolder.setContext(contexto);
        securityContextRepository.saveContext(contexto, request, response);
        return "redirect:/";
    }

    @PostMapping("/contas/remover")
    public String remover(@RequestParam String token, @RequestParam(defaultValue = "/") String retorno,
                          HttpServletRequest request, HttpServletResponse response) {
        contaVinculadaService.revogar(token);
        cookie.remover(request, response, token);
        return "redirect:" + retorno;
    }
}
