package com.obraslink.config;

import com.obraslink.model.Categoria;
import com.obraslink.model.Cliente;
import com.obraslink.model.PapelUsuario;
import com.obraslink.model.Prestador;
import com.obraslink.model.Servico;
import com.obraslink.model.Usuario;
import com.obraslink.repository.CategoriaRepository;
import com.obraslink.repository.ClienteRepository;
import com.obraslink.repository.PrestadorRepository;
import com.obraslink.repository.ServicoRepository;
import com.obraslink.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

@Configuration
@RequiredArgsConstructor
public class DataSeeder {

    private static final String DEFAULT_SEED_PASSWORD = "seed123";
    private static final int SAMPLE_CLIENTE_COUNT = 12;
    private static final int SAMPLE_PRESTADOR_COUNT = 10;
    private static final int SAMPLE_BOTH_COUNT = 5;
    private static final int SAMPLE_SERVICO_MIN = 2;
    private static final int SAMPLE_SERVICO_MAX = 4;

    private static final String[] NOMES = {
            "Ana", "Bruno", "Carlos", "Daniela", "Eduardo", "Fernanda", "Gabriel", "Helena",
            "Igor", "Juliana", "Kleber", "Larissa", "Marcos", "Nadia", "Otavio", "Paula"
    };
    private static final String[] SOBRENOMES = {
            "Silva", "Souza", "Oliveira", "Lima", "Pereira", "Costa", "Araujo", "Santos"
    };
    private static final String[] DESCRICOES_PRESTADOR = {
            "Atendimento rapido com foco em acabamento e limpeza.",
            "Experiencia em obras residenciais e comerciais de pequeno porte.",
            "Equipe qualificada para reformas, manutencao e novas instalacoes.",
            "Execucao com prazos claros e acompanhamento durante toda a obra."
    };
    private static final String[] TITULOS_SERVICO = {
            "Reforma de ambiente",
            "Instalacao eletrica",
            "Reparo hidraulico",
            "Pintura interna",
            "Manutencao predial",
            "Assentamento de piso",
            "Projeto e execucao"
    };

    private final CategoriaRepository categoriaRepository;
    private final UsuarioRepository usuarioRepository;
    private final ClienteRepository clienteRepository;
    private final PrestadorRepository prestadorRepository;
    private final ServicoRepository servicoRepository;
    private final PasswordEncoder passwordEncoder;

    @Bean
    public CommandLineRunner initData() {
        return args -> {
            List<Categoria> categorias = seedCategorias();
            seedAdmin();
            seedSampleData(categorias);
        };
    }

    private List<Categoria> seedCategorias() {
        if (categoriaRepository.count() == 0) {
            List<Categoria> categorias = Arrays.asList(
                    Categoria.builder().nome("Pedreiro").descricao("Construção de paredes, muros, pisos e acabamentos.").icone("🧱").build(),
                    Categoria.builder().nome("Mestre de Obras").descricao("Coordenação e supervisão completa da obra.").icone("👷").build(),
                    Categoria.builder().nome("Empreiteira").descricao("Empresas especializadas para projetos completos.").icone("🏢").build(),
                    Categoria.builder().nome("Eletricista").descricao("Instalações elétricas residenciais e comerciais.").icone("⚡").build(),
                    Categoria.builder().nome("Encanador").descricao("Sistemas hidráulicos, manutenção e instalações.").icone("🔧").build(),
                    Categoria.builder().nome("Pintor").descricao("Pintura interna e externa com excelente acabamento.").icone("🎨").build()
            );
            categoriaRepository.saveAll(categorias);
            System.out.println("Categorias seed data initialized.");
        }

        return categoriaRepository.findAll();
    }

    private void seedAdmin() {
        if (!usuarioRepository.existsByEmail("admin@obraslink.com")) {
            Usuario admin = Usuario.builder()
                    .email("admin@obraslink.com")
                    .senha(passwordEncoder.encode("admin123"))
                    .papeis(Set.of(PapelUsuario.ADMIN))
                    .ativo(true)
                    .build();
            usuarioRepository.save(admin);
            System.out.println("Admin user initialized: admin@obraslink.com / admin123");
        }
    }

    private void seedSampleData(List<Categoria> categorias) {
        if (categorias.isEmpty()) {
            return;
        }

        Random random = new Random();

        for (int i = 1; i <= SAMPLE_CLIENTE_COUNT; i++) {
            seedClienteOnly(i, random);
        }

        for (int i = 1; i <= SAMPLE_PRESTADOR_COUNT; i++) {
            seedPrestadorOnly(i, categorias, random);
        }

        for (int i = 1; i <= SAMPLE_BOTH_COUNT; i++) {
            seedClienteAndPrestador(i, categorias, random);
        }

        System.out.printf("Sample data ready. Clientes: %d, Prestadores: %d, Servicos: %d%n",
                clienteRepository.count(),
                prestadorRepository.count(),
                servicoRepository.count());
    }

    private void seedClienteOnly(int index, Random random) {
        String email = String.format("seed.cliente%02d@obraslink.com", index);
        Usuario usuario = ensureUsuario(email, Set.of(PapelUsuario.CLIENTE));

        if (clienteRepository.findByUsuarioId(usuario.getId()).isPresent()) {
            return;
        }

        Cliente cliente = Cliente.builder()
                .nome(randomNomeCompleto(random))
                .email(email)
                .cpfCnpj(String.format("100%08d", index))
                .telefone(randomTelefone(random))
                .usuarioId(usuario.getId())
                .build();
        clienteRepository.save(cliente);
    }

    private void seedPrestadorOnly(int index, List<Categoria> categorias, Random random) {
        String email = String.format("seed.prestador%02d@obraslink.com", index);
        Usuario usuario = ensureUsuario(email, Set.of(PapelUsuario.PRESTADOR));

        Prestador prestador = prestadorRepository.findByUsuarioId(usuario.getId()).orElseGet(() -> {
            Prestador novoPrestador = Prestador.builder()
                    .nome(randomNomeCompleto(random))
                    .email(email)
                    .cpfCnpj(String.format("300%08d", index))
                    .descricao(randomDescricaoPrestador(random))
                    .categoria(randomCategoria(categorias, random))
                    .precoMinimo(randomPrecoMinimo(random))
                    .anosExperiencia(1 + random.nextInt(15))
                    .usuarioId(usuario.getId())
                    .ativo(true)
                    .build();
            return prestadorRepository.save(novoPrestador);
        });

        seedServicos(prestador, categorias, random);
    }

    private void seedClienteAndPrestador(int index, List<Categoria> categorias, Random random) {
        String email = String.format("seed.ambos%02d@obraslink.com", index);
        Usuario usuario = ensureUsuario(email, Set.of(PapelUsuario.CLIENTE, PapelUsuario.PRESTADOR));

        if (clienteRepository.findByUsuarioId(usuario.getId()).isEmpty()) {
            Cliente cliente = Cliente.builder()
                    .nome(randomNomeCompleto(random))
                    .email(email)
                    .cpfCnpj(String.format("200%08d", index))
                    .telefone(randomTelefone(random))
                    .usuarioId(usuario.getId())
                    .build();
            clienteRepository.save(cliente);
        }

        Prestador prestador = prestadorRepository.findByUsuarioId(usuario.getId()).orElseGet(() -> {
            Prestador novoPrestador = Prestador.builder()
                    .nome(randomNomeCompleto(random))
                    .email(email)
                    .cpfCnpj(String.format("400%08d", index))
                    .descricao(randomDescricaoPrestador(random))
                    .categoria(randomCategoria(categorias, random))
                    .precoMinimo(randomPrecoMinimo(random))
                    .anosExperiencia(2 + random.nextInt(18))
                    .usuarioId(usuario.getId())
                    .ativo(true)
                    .build();
            return prestadorRepository.save(novoPrestador);
        });

        seedServicos(prestador, categorias, random);
    }

    private void seedServicos(Prestador prestador, List<Categoria> categorias, Random random) {
        if (!servicoRepository.findByPrestadorId(prestador.getId()).isEmpty()) {
            return;
        }

        int quantidade = SAMPLE_SERVICO_MIN + random.nextInt(SAMPLE_SERVICO_MAX - SAMPLE_SERVICO_MIN + 1);
        List<Servico> servicos = new ArrayList<>(quantidade);
        for (int i = 0; i < quantidade; i++) {
            String tituloBase = TITULOS_SERVICO[random.nextInt(TITULOS_SERVICO.length)];
            Categoria categoria = randomCategoria(categorias, random);
            Servico servico = Servico.builder()
                    .titulo(tituloBase + " " + (i + 1))
                    .descricao("Servico executado por profissional qualificado em " + categoria.getNome().toLowerCase() + ".")
                    .preco(randomPrecoServico(random))
                    .prestador(prestador)
                    .categoria(categoria)
                    .disponivel(random.nextInt(10) != 0)
                    .build();
            servicos.add(servico);
        }

        servicoRepository.saveAll(servicos);
    }

    private Usuario ensureUsuario(String email, Set<PapelUsuario> papeisDesejados) {
        return usuarioRepository.findByEmail(email).map(usuarioExistente -> {
            boolean precisaAtualizar = false;

            Set<PapelUsuario> papeisAtuais = new HashSet<>(usuarioExistente.getPapeis());
            if (!papeisAtuais.containsAll(papeisDesejados)) {
                papeisAtuais.addAll(papeisDesejados);
                usuarioExistente.setPapeis(papeisAtuais);
                precisaAtualizar = true;
            }

            if (!Boolean.TRUE.equals(usuarioExistente.getAtivo())) {
                usuarioExistente.setAtivo(true);
                precisaAtualizar = true;
            }

            if (precisaAtualizar) {
                return usuarioRepository.save(usuarioExistente);
            }
            return usuarioExistente;
        }).orElseGet(() -> usuarioRepository.save(Usuario.builder()
                .email(email)
                .senha(passwordEncoder.encode(DEFAULT_SEED_PASSWORD))
                .papeis(new HashSet<>(papeisDesejados))
                .ativo(true)
                .build()));
    }

    private String randomNomeCompleto(Random random) {
        String nome = NOMES[random.nextInt(NOMES.length)];
        String sobrenome = SOBRENOMES[random.nextInt(SOBRENOMES.length)];
        return nome + " " + sobrenome;
    }

    private String randomDescricaoPrestador(Random random) {
        return DESCRICOES_PRESTADOR[random.nextInt(DESCRICOES_PRESTADOR.length)];
    }

    private Categoria randomCategoria(List<Categoria> categorias, Random random) {
        return categorias.get(random.nextInt(categorias.size()));
    }

    private String randomTelefone(Random random) {
        return String.format("119%08d", random.nextInt(100000000));
    }

    private BigDecimal randomPrecoMinimo(Random random) {
        return BigDecimal.valueOf(100 + random.nextInt(500));
    }

    private BigDecimal randomPrecoServico(Random random) {
        return BigDecimal.valueOf(80 + random.nextInt(700));
    }
}
