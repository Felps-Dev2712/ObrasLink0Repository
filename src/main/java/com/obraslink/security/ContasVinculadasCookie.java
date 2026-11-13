package com.obraslink.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Cookie com os tokens das contas já logadas neste navegador (ver {@link com.obraslink.service.ContaVinculadaService}):
 * só os tokens, nunca senha ou dado da conta. HttpOnly porque não precisa de JavaScript, e um limite de contas para
 * não deixar o cookie crescer sem fim.
 */
@Component
public class ContasVinculadasCookie {
    static final String NOME = "obl_contas";
    static final int LIMITE_CONTAS = 6;
    // Vírgula não é um caractere válido em valor de cookie (RFC 6265); os tokens são base64url e nunca têm ponto.
    private static final String SEPARADOR = ".";

    public List<String> ler(HttpServletRequest request) {
        if (request.getCookies() == null) return List.of();
        return Arrays.stream(request.getCookies())
                .filter(c -> NOME.equals(c.getName()) && !c.getValue().isBlank())
                .findFirst()
                .map(c -> List.of(c.getValue().split("\\" + SEPARADOR)))
                .orElse(List.of());
    }

    /** Adiciona (ou atualiza, se já lá) um token à lista existente neste navegador e grava o cookie. */
    public void adicionar(HttpServletRequest request, HttpServletResponse response, String tokenBruto) {
        var tokens = new LinkedHashSet<>(ler(request));
        tokens.remove(tokenBruto);
        tokens.add(tokenBruto);
        escrever(request, response, new ArrayList<>(tokens));
    }

    public void remover(HttpServletRequest request, HttpServletResponse response, String tokenBruto) {
        var tokens = new ArrayList<>(ler(request));
        tokens.remove(tokenBruto);
        escrever(request, response, tokens);
    }

    public void escrever(HttpServletRequest request, HttpServletResponse response, List<String> tokens) {
        List<String> limitados = tokens.size() > LIMITE_CONTAS
                ? tokens.subList(tokens.size() - LIMITE_CONTAS, tokens.size()) : tokens;
        var cookie = ResponseCookie.from(NOME, String.join(SEPARADOR, limitados))
                .httpOnly(true).secure(request.isSecure()).sameSite("Lax").path("/").maxAge(60L * 60 * 24 * 30).build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
