package com.obraslink.config;

import com.obraslink.model.PapelUsuario;
import com.obraslink.model.Usuario;
import com.obraslink.service.AcessoService;
import com.obraslink.service.ClienteService;
import com.obraslink.service.PrestadorService;
import com.obraslink.service.NotificacaoService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@ControllerAdvice
@RequiredArgsConstructor
public class GlobalModelAttributes {

    private final AcessoService acessoService;
    private final ClienteService clienteService;
    private final PrestadorService prestadorService;
    private final NotificacaoService notificacaoService;
    private final com.obraslink.service.PerfilAtivoService perfilAtivoService;

    // Usado pelo layout para destacar o item ativo do menu e voltar à mesma página ao trocar de perfil.
    @ModelAttribute("currentPath")
    public String currentPath(jakarta.servlet.http.HttpServletRequest request) {
        return request.getRequestURI();
    }

    @ModelAttribute("auth")
    public Map<String, Object> auth() {
        Optional<Usuario> usuarioOpt = acessoService.getUsuarioAtual();
        if (usuarioOpt.isEmpty()) {
            return Map.of(
                    "authenticated", false,
                    "isAdmin", false,
                    "hasCliente", false,
                    "hasPrestador", false
            );
        }

        Usuario usuario = usuarioOpt.get();
        var cliente = clienteService.findByUsuarioId(usuario.getId());
        var prestador = prestadorService.findByUsuarioId(usuario.getId());
        Long clienteId = cliente.map(c -> c.getId()).orElse(null);
        Long prestadorId = prestador.map(p -> p.getId()).orElse(null);

        Map<String, Object> auth = new HashMap<>();
        auth.put("authenticated", true);
        auth.put("isAdmin", acessoService.isAdmin(usuario));
        auth.put("hasCliente", usuario.getPapeis().contains(PapelUsuario.CLIENTE));
        auth.put("hasPrestador", usuario.getPapeis().contains(PapelUsuario.PRESTADOR));
        auth.put("usuarioId", usuario.getId());
        auth.put("email", usuario.getEmail());
        auth.put("nome", cliente.map(c -> c.getNome()).filter(n -> !n.isBlank())
                .orElseGet(() -> prestador.map(p -> p.getNome()).filter(n -> !n.isBlank()).orElse("Minha conta")));
        auth.put("clienteId", clienteId);
        auth.put("prestadorId", prestadorId);
        auth.put("notificacoesNaoLidas", notificacaoService.contarNaoLidas(usuario.getId()));
        var perfilAtivo=perfilAtivoService.atual();
        // Foto do perfil em uso (cliente ou prestador); se ele não tiver, usa a do outro perfil da conta
        String fotoCliente = cliente.map(c -> c.getFotoPerfilChave()).orElse(null);
        String fotoPrestador = prestador.map(p -> p.getFotoPerfilChave()).orElse(null);
        auth.put("fotoPerfil", perfilAtivo == PapelUsuario.CLIENTE
                ? (fotoCliente != null ? fotoCliente : fotoPrestador)
                : (fotoPrestador != null ? fotoPrestador : fotoCliente));
        auth.put("perfilAtivo",perfilAtivo==null?"ADMIN":perfilAtivo.name());
        return auth;
    }
}
