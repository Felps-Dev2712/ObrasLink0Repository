package com.obraslink.security;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ContasVinculadasCookieTest {
    ContasVinculadasCookie cookie = new ContasVinculadasCookie();

    private MockHttpServletRequest requestCom(String... tokens) {
        var request = new MockHttpServletRequest();
        if (tokens.length > 0) request.setCookies(new Cookie(ContasVinculadasCookie.NOME, String.join(".", tokens)));
        return request;
    }

    private String valorGravado(MockHttpServletResponse response) {
        return response.getHeader("Set-Cookie");
    }

    @Test
    void semCookieDevolveListaVazia() {
        assertEquals(List.of(), cookie.ler(new MockHttpServletRequest()));
    }

    @Test
    void leOsTokensSeparadosPorPonto() {
        assertEquals(List.of("aaa", "bbb"), cookie.ler(requestCom("aaa", "bbb")));
    }

    @Test
    void adicionarSomaAoQueJaExistiaSemDuplicar() {
        var request = requestCom("aaa");
        var response = new MockHttpServletResponse();
        cookie.adicionar(request, response, "bbb");
        assertTrue(valorGravado(response).contains("aaa.bbb"));

        // adicionar um token que já estava lá não duplica, só reordena pro fim (mais recente)
        var request2 = requestCom("aaa", "bbb");
        var response2 = new MockHttpServletResponse();
        cookie.adicionar(request2, response2, "aaa");
        assertTrue(valorGravado(response2).contains("bbb.aaa"));
    }

    @Test
    void removerTiraSoAqueleToken() {
        var request = requestCom("aaa", "bbb", "ccc");
        var response = new MockHttpServletResponse();
        cookie.remover(request, response, "bbb");
        String gravado = valorGravado(response);
        assertTrue(gravado.contains("aaa.ccc"));
        assertFalse(gravado.contains("bbb"));
    }

    @Test
    void limitaAoNumeroMaximoDeContasMantendoAsMaisRecentes() {
        var tokens = new String[ContasVinculadasCookie.LIMITE_CONTAS + 2];
        for (int i = 0; i < tokens.length; i++) tokens[i] = "t" + i;
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        cookie.escrever(request, response, List.of(tokens));
        String gravado = valorGravado(response);
        assertFalse(gravado.contains("t0."), "o mais antigo cai fora do limite");
        assertTrue(gravado.contains("t" + (tokens.length - 1)), "o mais recente fica");
    }

    @Test
    void cookieEHttpOnlyESameSiteLax() {
        var response = new MockHttpServletResponse();
        cookie.escrever(new MockHttpServletRequest(), response, List.of("aaa"));
        String gravado = valorGravado(response);
        assertTrue(gravado.contains("HttpOnly"));
        assertTrue(gravado.contains("SameSite=Lax"));
    }
}
