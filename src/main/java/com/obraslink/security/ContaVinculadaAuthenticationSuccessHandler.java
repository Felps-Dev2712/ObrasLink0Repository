package com.obraslink.security;

import com.obraslink.repository.UsuarioRepository;
import com.obraslink.service.ContaVinculadaService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Depois de qualquer login bem-sucedido (o de sempre, ou "Adicionar conta" com a sessão de outra conta já aberta),
 * guarda um token de troca sem senha para esta conta neste navegador, somando-se às contas já guardadas antes
 * (nunca substitui): é o que alimenta o "trocar de conta" no menu, sem precisar digitar a senha de novo depois.
 * Também marca, com um cookie bem curto, que a pessoa acabou de logar — é o que faz o mascote Tchuros aparecer
 * na home a cada login (ver tchuros.js), não só na primeira vez.
 */
@Component
public class ContaVinculadaAuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {
    private final UsuarioRepository usuarios;
    private final ContaVinculadaService contaVinculadaService;
    private final ContasVinculadasCookie cookie;

    public ContaVinculadaAuthenticationSuccessHandler(UsuarioRepository usuarios, ContaVinculadaService contaVinculadaService,
                                                        ContasVinculadasCookie cookie) {
        this.usuarios = usuarios;
        this.contaVinculadaService = contaVinculadaService;
        this.cookie = cookie;
        setAlwaysUseDefaultTargetUrl(true);
        setDefaultTargetUrl("/");
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException, jakarta.servlet.ServletException {
        usuarios.findByEmail(authentication.getName()).ifPresent(usuario -> {
            String token = contaVinculadaService.vincular(usuario.getId());
            cookie.adicionar(request, response, token);
        });
        var tchuros = ResponseCookie.from("obl_tchuros_login", "1")
                .path("/").maxAge(15).sameSite("Lax").secure(request.isSecure()).build();
        response.addHeader(HttpHeaders.SET_COOKIE, tchuros.toString());
        super.onAuthenticationSuccess(request, response, authentication);
    }
}
