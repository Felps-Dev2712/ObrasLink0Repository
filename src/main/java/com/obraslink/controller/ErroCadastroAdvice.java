package com.obraslink.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@ControllerAdvice(assignableTypes = {ClienteController.class, PrestadorController.class})
public class ErroCadastroAdvice {
    @ExceptionHandler({IllegalArgumentException.class, DataIntegrityViolationException.class})
    public String dadosInvalidos(Exception error, HttpServletRequest request, RedirectAttributes attributes) {
        attributes.addFlashAttribute("mensagemErro", error instanceof IllegalArgumentException
                ? error.getMessage() : "Não foi possível salvar: confira se e-mail ou CPF/CNPJ já estão cadastrados.");
        String path = request.getServletPath();
        if (path.contains("/minha-conta")) return "redirect:/clientes";
        if (path.matches("/(clientes|prestadores)/[0-9]+")) return "redirect:" + path + "/editar";
        return path.startsWith("/prestadores") ? "redirect:/prestadores/novo" : "redirect:/clientes/novo";
    }
}
