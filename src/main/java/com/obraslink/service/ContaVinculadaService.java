package com.obraslink.service;

import com.obraslink.model.ContaVinculada;
import com.obraslink.model.Usuario;
import com.obraslink.repository.ContaVinculadaRepository;
import com.obraslink.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Troca de conta sem digitar senha de novo: cada login deste navegador recebe um token aleatório de alta entropia,
 * guardado só no cookie do cliente; aqui fica apenas o hash (SHA-256), para nunca ter o valor usável em texto puro
 * no banco, no mesmo espírito de um token de "lembrar-me". Expira em 30 dias de inatividade.
 */
@Service
public class ContaVinculadaService {
    public static final int DIAS_EXPIRACAO = 30;
    private static final SecureRandom ALEATORIO = new SecureRandom();

    private final ContaVinculadaRepository contas;
    private final UsuarioRepository usuarios;
    private final ClienteService clientes;
    private final PrestadorService prestadores;

    public ContaVinculadaService(ContaVinculadaRepository contas, UsuarioRepository usuarios,
                                 ClienteService clientes, PrestadorService prestadores) {
        this.contas = contas;
        this.usuarios = usuarios;
        this.clientes = clientes;
        this.prestadores = prestadores;
    }

    public record ContaResumo(Long usuarioId, String token, String nome, String email, String fotoPerfil, boolean ativa) {}

    private static String hash(String tokenBruto) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(tokenBruto.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e); // SHA-256 é garantido pela JVM
        }
    }

    /** Gera um token novo para esta conta e devolve o valor bruto, para ir só no cookie do navegador. */
    @Transactional
    public String vincular(Long usuarioId) {
        byte[] aleatorio = new byte[32];
        ALEATORIO.nextBytes(aleatorio);
        String tokenBruto = Base64.getUrlEncoder().withoutPadding().encodeToString(aleatorio);
        LocalDateTime agora = LocalDateTime.now();
        contas.save(ContaVinculada.builder().usuarioId(usuarioId).tokenHash(hash(tokenBruto))
                .criadoEm(agora).expiraEm(agora.plusDays(DIAS_EXPIRACAO)).ultimoAcessoEm(agora).build());
        return tokenBruto;
    }

    /** Usuário dono de um token válido (não expirado), ou vazio se o token não existe mais / expirou. */
    @Transactional
    public Optional<Usuario> autenticar(String tokenBruto) {
        return contas.findByTokenHash(hash(tokenBruto))
                .filter(c -> c.getExpiraEm().isAfter(LocalDateTime.now()))
                .flatMap(c -> {
                    c.setUltimoAcessoEm(LocalDateTime.now());
                    return usuarios.findById(c.getUsuarioId());
                });
    }

    @Transactional
    public void revogar(String tokenBruto) {
        contas.deleteByTokenHash(hash(tokenBruto));
    }

    /**
     * Um resumo por CONTA ainda válida (para listar no menu da conta) — se o mesmo usuário tiver mais de um token
     * (ex.: fez login de novo neste navegador antes de o anterior expirar), fica só o mais recente dos dois.
     * Tokens expirados ou de conta já excluída somem da lista (e do cookie, quando ele for reescrito a seguir).
     */
    @Transactional(readOnly = true)
    public List<ContaResumo> listar(List<String> tokensBrutos, Long usuarioAtualId) {
        Map<Long, ContaResumo> porUsuario = new LinkedHashMap<>();
        for (String tokenBruto : tokensBrutos) {
            contas.findByTokenHash(hash(tokenBruto))
                    .filter(c -> c.getExpiraEm().isAfter(LocalDateTime.now()))
                    .flatMap(c -> usuarios.findById(c.getUsuarioId()))
                    .ifPresent(usuario -> porUsuario.put(usuario.getId(), resumoDe(usuario, tokenBruto, usuario.getId().equals(usuarioAtualId))));
        }
        return List.copyOf(porUsuario.values());
    }

    private ContaResumo resumoDe(Usuario usuario, String tokenBruto, boolean ativa) {
        var cliente = clientes.findByUsuarioId(usuario.getId());
        var prestador = prestadores.findByUsuarioId(usuario.getId());
        String nome = cliente.map(c -> c.getNome()).filter(n -> n != null && !n.isBlank())
                .orElseGet(() -> prestador.map(p -> p.getNome()).filter(n -> n != null && !n.isBlank()).orElse(usuario.getEmail()));
        String foto = cliente.map(c -> c.getFotoPerfilChave()).filter(f -> f != null)
                .orElseGet(() -> prestador.map(p -> p.getFotoPerfilChave()).orElse(null));
        return new ContaResumo(usuario.getId(), tokenBruto, nome, usuario.getEmail(), foto, ativa);
    }
}
