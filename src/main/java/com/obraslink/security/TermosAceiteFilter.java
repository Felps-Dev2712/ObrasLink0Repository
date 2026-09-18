package com.obraslink.security;

import com.obraslink.service.AcessoService;
import com.obraslink.service.TermosService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;
import java.io.IOException;
import java.util.Set;

// Instanciado somente na cadeia de segurança, depois da autenticação/autorização.
@RequiredArgsConstructor
public class TermosAceiteFilter extends OncePerRequestFilter {
    private final AcessoService acessoService;
    private final TermosService termosService;
    private static final Set<String> LIBERADOS = Set.of(
            "/termos", "/privacidade", "/termos/aceitar", "/logout", "/error",
            "/clientes/minha-conta/excluir");

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = UrlPathHelper.defaultInstance.getPathWithinApplication(request);
        return LIBERADOS.contains(path) || path.startsWith("/css/") || path.startsWith("/js/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        var usuario = acessoService.getUsuarioAtual();
        if (usuario.isPresent() && !termosService.aceitouVersaoAtual(usuario.get().getId())) {
            response.setHeader("Cache-Control", "no-store");
            response.setStatus(HttpServletResponse.SC_SEE_OTHER);
            response.setHeader("Location", request.getContextPath() + "/termos/aceitar");
            return;
        }
        chain.doFilter(request, response);
    }
}
