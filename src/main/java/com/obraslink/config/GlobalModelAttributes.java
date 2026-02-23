package com.obraslink.config;

import com.obraslink.model.PapelUsuario;
import com.obraslink.model.Usuario;
import com.obraslink.service.AcessoService;
import com.obraslink.service.ClienteService;
import com.obraslink.service.PrestadorService;
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
        Long clienteId = clienteService.findByUsuarioId(usuario.getId()).map(c -> c.getId()).orElse(null);
        Long prestadorId = prestadorService.findByUsuarioId(usuario.getId()).map(p -> p.getId()).orElse(null);

        Map<String, Object> auth = new HashMap<>();
        auth.put("authenticated", true);
        auth.put("isAdmin", acessoService.isAdmin(usuario));
        auth.put("hasCliente", usuario.getPapeis().contains(PapelUsuario.CLIENTE));
        auth.put("hasPrestador", usuario.getPapeis().contains(PapelUsuario.PRESTADOR));
        auth.put("usuarioId", usuario.getId());
        auth.put("email", usuario.getEmail());
        auth.put("clienteId", clienteId);
        auth.put("prestadorId", prestadorId);
        return auth;
    }
}
