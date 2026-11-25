package com.obraslink.config;

import com.obraslink.model.PapelUsuario;
import com.obraslink.model.Usuario;
import com.obraslink.repository.CategoriaRepository;
import com.obraslink.repository.UsuarioRepository;
import com.obraslink.service.ContaService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Inicialização do perfil "prod" (no lugar do {@link DataSeeder}):
 * <ul>
 *   <li>garante as categorias padrão do catálogo;</li>
 *   <li>exclui as contas de demonstração criadas pelo DataSeeder, se existirem (com tudo o que depende delas);</li>
 *   <li>mantém só o administrador ({@value DataSeeder#EMAIL_ADMIN}), com a senha de {@code OBRALINK_ADMIN_PASSWORD}.</li>
 * </ul>
 * A aplicação não sobe se o administrador ficaria com a senha pública de demonstração.
 */
@Slf4j
@Component
@Profile("prod")
@Order(0)
public class ProducaoBootstrap implements CommandLineRunner {

    static final int TAMANHO_MINIMO_SENHA = 12;

    private final UsuarioRepository usuarioRepository;
    private final CategoriaRepository categoriaRepository;
    private final ContaService contaService;
    private final PasswordEncoder passwordEncoder;
    private final String senhaAdmin;

    public ProducaoBootstrap(UsuarioRepository usuarioRepository, CategoriaRepository categoriaRepository,
                             ContaService contaService, PasswordEncoder passwordEncoder,
                             @Value("${obralink.admin.password:}") String senhaAdmin) {
        this.usuarioRepository = usuarioRepository;
        this.categoriaRepository = categoriaRepository;
        this.contaService = contaService;
        this.passwordEncoder = passwordEncoder;
        this.senhaAdmin = senhaAdmin == null ? "" : senhaAdmin;
    }

    @Override
    public void run(String... args) {
        // Toda validação vem antes de qualquer exclusão: configuração errada não pode apagar dados.
        validarConfiguracao();
        DataSeeder.garantirCategorias(categoriaRepository);
        removerContasDemonstracao();
        garantirAdministrador();
    }

    private void validarConfiguracao() {
        if (!senhaAdmin.isEmpty()) {
            if (senhaAdmin.length() < TAMANHO_MINIMO_SENHA) {
                throw new IllegalStateException("Perfil prod: OBRALINK_ADMIN_PASSWORD precisa ter pelo menos "
                        + TAMANHO_MINIMO_SENHA + " caracteres.");
            }
            return;
        }
        Optional<Usuario> admin = usuarioRepository.findByEmail(DataSeeder.EMAIL_ADMIN);
        if (admin.isEmpty()) {
            throw new IllegalStateException("Perfil prod: defina OBRALINK_ADMIN_PASSWORD para criar o administrador "
                    + DataSeeder.EMAIL_ADMIN + ".");
        }
        if (passwordEncoder.matches(DataSeeder.SENHA_ADMIN, admin.get().getSenha())) {
            throw new IllegalStateException("Perfil prod: o administrador ainda usa a senha pública de demonstração. "
                    + "Defina OBRALINK_ADMIN_PASSWORD.");
        }
    }

    void removerContasDemonstracao() {
        int removidas = 0;
        for (String email : DataSeeder.EMAILS_DEMONSTRACAO) {
            Optional<Usuario> conta = usuarioRepository.findByEmail(email);
            if (conta.isPresent()) {
                contaService.excluirConta(conta.get());
                removidas++;
            }
        }
        if (removidas > 0) {
            log.warn("Perfil prod: {} contas de demonstração excluídas.", removidas);
        }
    }

    /** Chamado depois de {@link #validarConfiguracao()}: sem senha configurada, o admin já existe com senha própria. */
    void garantirAdministrador() {
        Optional<Usuario> existente = usuarioRepository.findByEmail(DataSeeder.EMAIL_ADMIN);
        if (existente.isEmpty()) {
            usuarioRepository.save(Usuario.builder().email(DataSeeder.EMAIL_ADMIN).nome("Administrador")
                    .senha(passwordEncoder.encode(senhaAdmin))
                    .papeis(new HashSet<>(Set.of(PapelUsuario.ADMIN))).ativo(true).build());
            log.info("Perfil prod: administrador {} criado.", DataSeeder.EMAIL_ADMIN);
            return;
        }
        Usuario admin = existente.get();
        if (!senhaAdmin.isEmpty() && !passwordEncoder.matches(senhaAdmin, admin.getSenha())) {
            admin.setSenha(passwordEncoder.encode(senhaAdmin));
            usuarioRepository.save(admin);
            log.info("Perfil prod: senha do administrador atualizada a partir de OBRALINK_ADMIN_PASSWORD.");
        }
    }
}
