package com.obraslink.service;

import com.obraslink.model.PapelUsuario;
import com.obraslink.model.Usuario;
import com.obraslink.repository.ClienteRepository;
import com.obraslink.repository.PrestadorRepository;
import com.obraslink.repository.ServicoRepository;
import com.obraslink.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AcessoService {

    private final UsuarioRepository usuarioRepository;
    private final ClienteRepository clienteRepository;
    private final PrestadorRepository prestadorRepository;
    private final ServicoRepository servicoRepository;

    public Optional<Usuario> getUsuarioAtual() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }

        String email = authentication.getName();
        return usuarioRepository.findByEmail(email);
    }

    public Usuario getUsuarioAtualOrThrow() {
        return getUsuarioAtual().orElseThrow(() -> new IllegalStateException("Usuario autenticado nao encontrado."));
    }

    public boolean isAdmin() {
        return getUsuarioAtual().map(this::isAdmin).orElse(false);
    }

    public boolean isAdmin(Usuario usuario) {
        return usuario.getPapeis().contains(PapelUsuario.ADMIN);
    }

    public boolean hasPapel(PapelUsuario papel) {
        return getUsuarioAtual().map(usuario -> usuario.getPapeis().contains(papel)).orElse(false);
    }

    public boolean podeGerenciarCliente(Long clienteId) {
        return getUsuarioAtual().map(usuario -> {
            if (isAdmin(usuario)) {
                return true;
            }
            return clienteRepository.existsByIdAndUsuarioId(clienteId, usuario.getId());
        }).orElse(false);
    }

    public boolean podeGerenciarPrestador(Long prestadorId) {
        return getUsuarioAtual().map(usuario -> {
            if (isAdmin(usuario)) {
                return true;
            }
            return prestadorRepository.existsByIdAndUsuarioId(prestadorId, usuario.getId());
        }).orElse(false);
    }

    public boolean podeGerenciarServico(Long servicoId) {
        return getUsuarioAtual().map(usuario -> {
            if (isAdmin(usuario)) {
                return true;
            }
            return servicoRepository.existsByIdAndPrestadorUsuarioId(servicoId, usuario.getId());
        }).orElse(false);
    }
}
