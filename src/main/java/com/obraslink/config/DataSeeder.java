package com.obraslink.config;

import com.obraslink.model.*;
import com.obraslink.repository.*;
import com.obraslink.service.TermosService;
import com.obraslink.validation.DadosCadastrais;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

/**
 * Dados fictícios para testar a plataforma localmente.
 * Só roda com o banco vazio (nenhum usuário): nunca mexe em dados existentes.
 * As contas e senhas estão documentadas em docs/contas-de-teste.md.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class DataSeeder {

    static final String SENHA_TESTE = "Obras@2026";
    static final String SENHA_ADMIN = "admin123";

    private final CategoriaRepository categoriaRepository;
    private final UsuarioRepository usuarioRepository;
    private final ClienteRepository clienteRepository;
    private final PrestadorRepository prestadorRepository;
    private final ServicoRepository servicoRepository;
    private final AtendimentoRepository atendimentoRepository;
    private final AvaliacaoPrestadorRepository avaliacaoPrestadorRepository;
    private final AvaliacaoClienteRepository avaliacaoClienteRepository;
    private final FavoritoRepository favoritoRepository;
    private final DisponibilidadeRepository disponibilidadeRepository;
    private final CertificacaoRepository certificacaoRepository;
    private final NotificacaoRepository notificacaoRepository;
    private final AceiteTermosRepository aceiteTermosRepository;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbc;

    @Value("${obralink.upload-dir:./uploads}")
    private String uploadDir;

    /** Endereços fictícios em bairros reais de São Paulo e região (coordenadas aproximadas). */
    private record Local(String cep, String rua, String numero, String bairro, String cidade, double lat, double lng) {
        Endereco endereco() {
            return Endereco.builder().cep(cep).logradouro(rua).numero(numero).bairro(bairro)
                    .cidade(cidade).estado("SP").latitude(lat).longitude(lng).build();
        }
    }

    private static final Local PINHEIROS = new Local("05422-001", "Rua dos Pinheiros", "500", "Pinheiros", "São Paulo", -23.5660, -46.6830);
    private static final Local VILA_MARIANA = new Local("04010-100", "Rua Domingos de Morais", "1000", "Vila Mariana", "São Paulo", -23.5880, -46.6370);
    private static final Local MOEMA = new Local("04029-100", "Avenida Ibirapuera", "2000", "Moema", "São Paulo", -23.6000, -46.6640);
    private static final Local SANTANA = new Local("02011-000", "Rua Voluntários da Pátria", "1500", "Santana", "São Paulo", -23.5030, -46.6250);
    private static final Local TATUAPE = new Local("03081-000", "Rua Tuiuti", "1200", "Tatuapé", "São Paulo", -23.5400, -46.5760);
    private static final Local BUTANTA = new Local("05503-001", "Avenida Vital Brasil", "800", "Butantã", "São Paulo", -23.5710, -46.7080);
    private static final Local LAPA = new Local("05033-000", "Rua Guaicurus", "600", "Lapa", "São Paulo", -23.5250, -46.7020);
    private static final Local SANTO_AMARO = new Local("04734-002", "Avenida Adolfo Pinheiro", "1500", "Santo Amaro", "São Paulo", -23.6480, -46.7020);
    private static final Local MOOCA = new Local("03104-002", "Rua da Mooca", "2000", "Mooca", "São Paulo", -23.5580, -46.5950);
    private static final Local ITAQUERA = new Local("08295-000", "Avenida Itaquera", "5000", "Itaquera", "São Paulo", -23.5400, -46.4600);
    private static final Local BELA_VISTA = new Local("01310-100", "Avenida Paulista", "1000", "Bela Vista", "São Paulo", -23.5640, -46.6520);
    private static final Local PERDIZES = new Local("05013-000", "Rua Cardoso de Almeida", "800", "Perdizes", "São Paulo", -23.5370, -46.6730);
    private static final Local IPIRANGA = new Local("04203-000", "Rua Bom Pastor", "1000", "Ipiranga", "São Paulo", -23.5880, -46.6080);
    private static final Local GUARULHOS = new Local("07061-000", "Avenida Timóteo Penteado", "500", "Vila Galvão", "Guarulhos", -23.4590, -46.5330);
    private static final Local SANTO_ANDRE = new Local("09010-000", "Rua Coronel Oliveira Lima", "300", "Centro", "Santo André", -23.6560, -46.5320);
    private static final Local OSASCO = new Local("06020-010", "Avenida dos Autonomistas", "1500", "Vila Yara", "Osasco", -23.5320, -46.7790);
    private static final Local CAMPINAS = new Local("13012-000", "Avenida Francisco Glicério", "1000", "Centro", "Campinas", -22.9050, -47.0600);

    /** Profissional de demonstração: nome, categoria, local, raio, anos, preço mínimo, descrição e serviços. */
    private record Pro(String email, String nome, String categoria, Local local, int raioKm, int anos, int preco,
                       String descricao, String[][] servicos) { }

    private int seq = 0;

    @Bean
    @Order(0)
    public CommandLineRunner initData() {
        return args -> {
            if (usuarioRepository.count() > 0) {
                log.info("Banco já tem usuários: dados de demonstração não foram recriados.");
                return;
            }
            semear();
        };
    }

    private void semear() {
        Map<String, Categoria> cat = semearCategorias();
        criarUsuario("admin@obraslink.com", "Administrador", SENHA_ADMIN, Set.of(PapelUsuario.ADMIN), 240);

        // ---------- Contas de teste principais ----------
        Usuario uCliente = criarUsuario("cliente.teste@obraslink.com", "Camila Teste", SENHA_TESTE, Set.of(PapelUsuario.CLIENTE), 200);
        Cliente clienteTeste = criarCliente(uCliente, "Camila Teste", VILA_MARIANA, 200);

        Pro proTeste = new Pro("prestador.teste@obraslink.com", "Rafael Teste Elétrica", "Eletricista", PINHEIROS, 20, 12, 150,
                "Eletricista com 12 anos de experiência em reformas residenciais e comerciais. Quadros de distribuição, troca de fiação, "
                        + "iluminação em LED e laudos. Atendo com hora marcada e deixo o ambiente limpo.",
                new String[][]{
                        {"Troca de quadro de distribuição", "Substituição do quadro antigo por um novo com disjuntores DR e DPS.", "850"},
                        {"Instalação de iluminação LED", "Projeto e instalação de spots, fitas e pendentes em LED.", "320"},
                        {"Revisão elétrica completa", "Inspeção de tomadas, fiação e aterramento com relatório.", "450"},
                        {"Instalação de chuveiro e tomadas", "Ponto novo de 220 V, chuveiro elétrico e tomadas extras.", "180"}});
        Usuario uPrestador = criarUsuario(proTeste.email(), proTeste.nome(), SENHA_TESTE, Set.of(PapelUsuario.PRESTADOR), 400);
        Prestador prestadorTeste = criarPrestador(uPrestador, proTeste, cat, 400);

        Usuario uAmbos = criarUsuario("ambos.teste@obraslink.com", "Lucas Teste", SENHA_TESTE, Set.of(PapelUsuario.CLIENTE, PapelUsuario.PRESTADOR), 150);
        Cliente clienteAmbos = criarCliente(uAmbos, "Lucas Teste", MOEMA, 150);
        Prestador prestadorAmbos = criarPrestador(uAmbos, new Pro(uAmbos.getEmail(), "Lucas Teste Pinturas", "Pintor", MOEMA, 10, 6, 200,
                "Pintura residencial interna e externa, textura e grafiato. Orçamento sem compromisso na zona sul.",
                new String[][]{
                        {"Pintura de apartamento", "Pintura completa de apartamento de até 70 m², com massa corrida.", "2800"},
                        {"Textura e grafiato", "Aplicação de textura em fachadas e muros.", "45"}}), cat, 150);

        // ---------- Outros profissionais ----------
        List<Pro> outros = List.of(
                new Pro("joao.pedreiro@obraslink.com", "João Batista Construções", "Pedreiro", BUTANTA, 15, 20, 250,
                        "Alvenaria, contrapiso, reboco e assentamento de pisos. Equipe de 3 pessoas.",
                        new String[][]{{"Assentamento de porcelanato", "Assentamento de porcelanato com nivelamento, preço por m².", "70"},
                                {"Construção de muro", "Muro em bloco com reboco e pintura, preço por m².", "180"},
                                {"Contrapiso", "Contrapiso nivelado para receber piso, preço por m².", "45"}}),
                new Pro("marta.mestre@obraslink.com", "Marta Oliveira Gestão de Obras", "Mestre de Obras", BELA_VISTA, 25, 18, 3000,
                        "Coordeno reformas completas: cronograma, compra de material e equipe.",
                        new String[][]{{"Gestão de reforma completa", "Acompanhamento diário da obra do início ao fim, valor mensal.", "6500"},
                                {"Vistoria técnica", "Visita técnica com relatório de patologias e orçamento.", "400"}}),
                new Pro("construtora.horizonte@obraslink.com", "Construtora Horizonte", "Empreiteira", SANTO_ANDRE, 40, 15, 15000,
                        "Empreiteira para obras residenciais e comerciais no ABC e em São Paulo.",
                        new String[][]{{"Construção de casa térrea", "Construção completa por m², do alicerce ao acabamento.", "2400"},
                                {"Reforma de loja", "Reforma comercial com entrega em prazo fechado.", "35000"}}),
                new Pro("paulo.eletrica@obraslink.com", "Paulo Santos Elétrica", "Eletricista", SANTANA, 12, 8, 120,
                        "Atendimento rápido na zona norte, urgências elétricas e manutenção.",
                        new String[][]{{"Visita para reparo elétrico", "Diagnóstico e reparo de curto-circuito ou disjuntor desarmando.", "150"},
                                {"Instalação de ventilador de teto", "Instalação com suporte e ligação no interruptor.", "160"}}),
                new Pro("ana.hidraulica@obraslink.com", "Ana Costa Hidráulica", "Encanador", PERDIZES, 15, 10, 130,
                        "Vazamentos, caça-vazamento com geofone, troca de registros e aquecedores.",
                        new String[][]{{"Caça-vazamento", "Localização de vazamento sem quebra-quebra.", "350"},
                                {"Troca de registro", "Troca de registro de gaveta ou pressão.", "220"},
                                {"Instalação de aquecedor a gás", "Instalação de aquecedor de passagem com teste de estanqueidade.", "480"}}),
                new Pro("diego.encanador@obraslink.com", "Diego Lima Encanamentos", "Encanador", TATUAPE, 10, 5, 100,
                        "Desentupimentos e reparos hidráulicos na zona leste.",
                        new String[][]{{"Desentupimento de pia e ralo", "Desentupimento com máquina elétrica.", "180"},
                                {"Troca de sifão e torneira", "Troca de peças hidráulicas de cozinha e banheiro.", "120"}}),
                new Pro("cores.pinturas@obraslink.com", "Cores & Cia Pinturas", "Pintor", LAPA, 20, 14, 300,
                        "Pintura de apartamentos, casas e condomínios com equipe própria.",
                        new String[][]{{"Pintura de fachada", "Pintura de fachada com lavagem prévia, preço por m².", "38"},
                                {"Pintura de quarto", "Pintura de quarto com até 12 m² de piso.", "650"}}),
                new Pro("madeira.nobre@obraslink.com", "Madeira Nobre Marcenaria", "Marceneiro", MOOCA, 30, 22, 1200,
                        "Móveis planejados sob medida em MDF e madeira maciça.",
                        new String[][]{{"Cozinha planejada", "Projeto 3D e fabricação de cozinha planejada.", "9800"},
                                {"Guarda-roupa sob medida", "Guarda-roupa com portas de correr e iluminação.", "5200"}}),
                new Pro("gesso.arte@obraslink.com", "Gesso Arte Acabamentos", "Gesseiro", IPIRANGA, 18, 9, 90,
                        "Forro de gesso, drywall, sancas e molduras.",
                        new String[][]{{"Forro de gesso", "Forro liso com acabamento, preço por m².", "85"},
                                {"Parede de drywall", "Parede divisória com isolamento acústico, preço por m².", "140"}}),
                new Pro("osasco.reformas@obraslink.com", "Silva Reformas Osasco", "Pedreiro", OSASCO, 12, 7, 200,
                        "Pequenas reformas, banheiros e cozinhas em Osasco e zona oeste.",
                        new String[][]{{"Reforma de banheiro", "Troca de revestimento, louças e metais.", "7500"}}),
                new Pro("guarulhos.eletrica@obraslink.com", "Elétrica Guarulhos", "Eletricista", GUARULHOS, 8, 4, 100,
                        "Eletricista em Guarulhos para residências e pequenos comércios.",
                        new String[][]{{"Instalação de tomada", "Ponto de tomada novo com eletroduto aparente.", "120"}}),
                new Pro("campinas.obras@obraslink.com", "Campinas Obras e Projetos", "Mestre de Obras", CAMPINAS, 30, 16, 2500,
                        "Atendemos Campinas e região. Fora da Grande São Paulo, ótimo para testar o filtro de região.",
                        new String[][]{{"Acompanhamento de obra", "Gestão mensal de obra residencial.", "5800"}}));
        List<Prestador> prestadores = new ArrayList<>();
        prestadores.add(prestadorTeste);
        prestadores.add(prestadorAmbos);
        int dias = 380;
        for (Pro p : outros) {
            Usuario u = criarUsuario(p.email(), p.nome(), SENHA_TESTE, Set.of(PapelUsuario.PRESTADOR), dias);
            prestadores.add(criarPrestador(u, p, cat, dias));
            dias -= 25;
        }

        // ---------- Outros clientes ----------
        Object[][] dadosClientes = {
                {"beatriz.cliente@obraslink.com", "Beatriz Almeida", PINHEIROS},
                {"carlos.cliente@obraslink.com", "Carlos Mendes", SANTANA},
                {"fernanda.cliente@obraslink.com", "Fernanda Rocha", TATUAPE},
                {"gustavo.cliente@obraslink.com", "Gustavo Ferreira", SANTO_AMARO},
                {"helena.cliente@obraslink.com", "Helena Martins", ITAQUERA},
                {"igor.cliente@obraslink.com", "Igor Nascimento", BUTANTA}};
        List<Usuario> usuariosClientes = new ArrayList<>();
        List<Cliente> clientes = new ArrayList<>();
        dias = 300;
        for (Object[] d : dadosClientes) {
            Usuario u = criarUsuario((String) d[0], (String) d[1], SENHA_TESTE, Set.of(PapelUsuario.CLIENTE), dias);
            usuariosClientes.add(u);
            clientes.add(criarCliente(u, (String) d[1], (Local) d[2], dias));
            dias -= 30;
        }

        semearAtendimentos(uCliente, clienteTeste, uPrestador, prestadorTeste, uAmbos, clienteAmbos, prestadores, usuariosClientes, clientes);
        semearFavoritos(clienteTeste, clienteAmbos, clientes, prestadores);
        semearDisponibilidade(prestadores);
        semearCertificacoes(prestadorTeste, prestadores);
        semearNotificacoes(uCliente, uPrestador, uAmbos, prestadorTeste);
        aceitarTermos();

        log.info("Dados de demonstração criados: {} usuários, {} profissionais, {} clientes, {} serviços, {} atendimentos.",
                usuarioRepository.count(), prestadorRepository.count(), clienteRepository.count(),
                servicoRepository.count(), atendimentoRepository.count());
    }

    // ---------- Cadastros ----------

    private Map<String, Categoria> semearCategorias() {
        if (categoriaRepository.count() == 0) {
            categoriaRepository.saveAll(List.of(
                    Categoria.builder().nome("Pedreiro").descricao("Alvenaria, contrapiso, reboco e assentamento de pisos.").icone("🧱").build(),
                    Categoria.builder().nome("Mestre de Obras").descricao("Coordenação e supervisão completa da obra.").icone("👷").build(),
                    Categoria.builder().nome("Empreiteira").descricao("Empresas para projetos completos, do alicerce ao acabamento.").icone("🏢").build(),
                    Categoria.builder().nome("Eletricista").descricao("Instalações elétricas residenciais e comerciais.").icone("⚡").build(),
                    Categoria.builder().nome("Encanador").descricao("Sistemas hidráulicos, vazamentos e instalações.").icone("🔧").build(),
                    Categoria.builder().nome("Pintor").descricao("Pintura interna e externa, texturas e acabamentos.").icone("🎨").build(),
                    Categoria.builder().nome("Marceneiro").descricao("Móveis planejados e sob medida.").icone("🪚").build(),
                    Categoria.builder().nome("Gesseiro").descricao("Forros de gesso, drywall, sancas e molduras.").icone("🧰").build()));
        }
        Map<String, Categoria> mapa = new HashMap<>();
        categoriaRepository.findAll().forEach(c -> mapa.put(c.getNome(), c));
        return mapa;
    }

    private Usuario criarUsuario(String email, String nome, String senha, Set<PapelUsuario> papeis, int diasAtras) {
        Usuario u = usuarioRepository.save(Usuario.builder().email(email).nome(nome).senha(passwordEncoder.encode(senha))
                .papeis(new HashSet<>(papeis)).ativo(true).build());
        recuar("usuarios", u.getId(), diasAtras);
        return u;
    }

    private Cliente criarCliente(Usuario u, String nome, Local local, int diasAtras) {
        seq++;
        Cliente c = clienteRepository.save(Cliente.builder().nome(nome).email(u.getEmail())
                .cpfCnpj(DocumentosDemonstracao.cpf("510", seq)).telefone(telefone(seq))
                .usuarioId(u.getId()).endereco(local.endereco()).build());
        recuar("clientes", c.getId(), diasAtras);
        return c;
    }

    private Prestador criarPrestador(Usuario u, Pro p, Map<String, Categoria> cat, int diasAtras) {
        seq++;
        Categoria categoria = cat.get(p.categoria());
        Prestador prestador = Prestador.builder().nome(p.nome()).email(u.getEmail())
                .cpfCnpj(DocumentosDemonstracao.cnpj("61", seq)).whatsapp(telefone(seq))
                .descricao(p.descricao()).categoria(categoria).precoMinimo(BigDecimal.valueOf(p.preco()))
                .anosExperiencia(p.anos()).raioAtendimentoKm(p.raioKm()).usuarioId(u.getId()).ativo(true)
                .endereco(p.local().endereco()).build();
        prestador.getCategorias().add(categoria);
        prestador = prestadorRepository.save(prestador);
        recuar("prestadores", prestador.getId(), diasAtras);

        List<Servico> servicos = new ArrayList<>();
        for (int i = 0; i < p.servicos().length; i++) {
            String[] s = p.servicos()[i];
            servicos.add(Servico.builder().titulo(s[0]).descricao(s[1]).preco(new BigDecimal(s[2]))
                    .prestador(prestador).categoria(categoria).disponivel(i < 3).build());
        }
        servicoRepository.saveAll(servicos);
        return prestador;
    }

    // ---------- Atendimentos e avaliações ----------

    private void semearAtendimentos(Usuario uCliente, Cliente clienteTeste, Usuario uPrestador, Prestador prestadorTeste,
                                    Usuario uAmbos, Cliente clienteAmbos, List<Prestador> prestadores,
                                    List<Usuario> usuariosClientes, List<Cliente> clientes) {
        String[] comentarios = {
                "Serviço impecável, chegou no horário e deixou tudo limpo.",
                "Muito educado e explicou tudo o que fez. Recomendo.",
                "Bom trabalho, só atrasou um pouco no segundo dia.",
                "Preço justo e acabamento caprichado.",
                "Resolveu um problema que outros dois profissionais não conseguiram."};

        // Cliente de teste com o prestador de teste: um de cada status.
        Atendimento concluidoAvaliado = atendimento(uCliente, clienteTeste.getNome(), uPrestador, prestadorTeste, Atendimento.Status.CONCLUIDO, 60);
        avaliarPrestador(concluidoAvaliado, prestadorTeste, clienteTeste, 5, comentarios[0], 55);
        avaliarCliente(concluidoAvaliado, 5, "Cliente atenciosa, deixou o local liberado e pagou em dia.", 55);
        atendimento(uCliente, clienteTeste.getNome(), uPrestador, prestadorTeste, Atendimento.Status.CONCLUIDO, 12); // sem avaliação: testar avaliar dos dois lados
        Atendimento andamento = atendimento(uCliente, clienteTeste.getNome(), uPrestador, prestadorTeste, Atendimento.Status.EM_ANDAMENTO, 4);
        andamento.setDataAgendada(LocalDate.now().plusDays(3));
        andamento.setHorarioAgendado(LocalTime.of(9, 0));
        atendimentoRepository.save(andamento);
        atendimento(uCliente, clienteTeste.getNome(), uPrestador, prestadorTeste, Atendimento.Status.CONTATO_SOLICITADO, 1);

        // Cliente de teste com outros profissionais.
        Atendimento comPintor = atendimento(uCliente, clienteTeste.getNome(), uAmbos, prestadores.get(1), Atendimento.Status.CONCLUIDO, 90);
        avaliarPrestador(comPintor, prestadores.get(1), clienteTeste, 4, comentarios[3], 85);
        atendimento(uCliente, clienteTeste.getNome(), usuarioDo(prestadores.get(6)), prestadores.get(6), Atendimento.Status.CONCLUIDO, 20);
        atendimento(uCliente, clienteTeste.getNome(), usuarioDo(prestadores.get(2)), prestadores.get(2), Atendimento.Status.CONTATO_SOLICITADO, 2);

        // Conta com os dois perfis contratando o prestador de teste.
        Atendimento ambosConcluido = atendimento(uAmbos, clienteAmbos.getNome(), uPrestador, prestadorTeste, Atendimento.Status.CONCLUIDO, 120);
        avaliarPrestador(ambosConcluido, prestadorTeste, clienteAmbos, 4, comentarios[1], 115);
        avaliarCliente(ambosConcluido, 4, "Combinado cumprido, só pediu mudanças no meio do serviço.", 115);

        // Histórico dos outros clientes: dá volume ao painel do prestador de teste (vários meses) e notas aos demais.
        int[][] historico = {{0, 0, 30}, {1, 0, 75}, {2, 0, 150}, {3, 0, 210}, {4, 0, 270}, {5, 0, 8},
                {0, 3, 40}, {1, 4, 100}, {2, 5, 65}, {3, 7, 130}, {4, 8, 45}, {5, 9, 25}, {0, 6, 160}, {1, 2, 200}};
        int n = 0;
        for (int[] h : historico) {
            Usuario uc = usuariosClientes.get(h[0]);
            Cliente c = clientes.get(h[0]);
            Prestador p = h[1] == 0 ? prestadorTeste : prestadores.get(h[1]);
            Usuario up = h[1] == 0 ? uPrestador : usuarioDo(p);
            Atendimento a = atendimento(uc, c.getNome(), up, p, Atendimento.Status.CONCLUIDO, h[2]);
            int nota = n % 4 == 2 ? 3 : (n % 3 == 0 ? 5 : 4);
            avaliarPrestador(a, p, c, nota, comentarios[n % comentarios.length], h[2] - 3);
            if (n % 2 == 0) avaliarCliente(a, 5 - (n % 3 == 1 ? 1 : 0), "Cliente pontual e organizado.", h[2] - 3);
            n++;
        }
        // Pedidos em aberto para o prestador de teste responder.
        atendimento(usuariosClientes.get(0), clientes.get(0).getNome(), uPrestador, prestadorTeste, Atendimento.Status.CONTATO_SOLICITADO, 0);
        atendimento(usuariosClientes.get(3), clientes.get(3).getNome(), uPrestador, prestadorTeste, Atendimento.Status.CONTATO_SOLICITADO, 2);
        Atendimento agendado = atendimento(usuariosClientes.get(2), clientes.get(2).getNome(), uPrestador, prestadorTeste, Atendimento.Status.EM_ANDAMENTO, 6);
        agendado.setDataAgendada(LocalDate.now().plusDays(1));
        agendado.setHorarioAgendado(LocalTime.of(14, 30));
        atendimentoRepository.save(agendado);
    }

    private final Map<Long, Usuario> usuariosPorPrestador = new HashMap<>();

    private Usuario usuarioDo(Prestador p) {
        return usuariosPorPrestador.computeIfAbsent(p.getId(), id -> usuarioRepository.findById(p.getUsuarioId()).orElseThrow());
    }

    private Atendimento atendimento(Usuario cliente, String clienteNome, Usuario prestadorUsuario, Prestador prestador,
                                    Atendimento.Status status, int diasAtras) {
        List<Servico> servicos = servicoRepository.findByPrestadorId(prestador.getId());
        Servico servico = servicos.isEmpty() ? null : servicos.get(Math.abs(diasAtras) % servicos.size());
        Atendimento a = new Atendimento();
        a.setClienteUsuarioId(cliente.getId());
        a.setPrestadorUsuarioId(prestadorUsuario.getId());
        a.setPrestadorId(prestador.getId());
        a.setClienteNome(clienteNome);
        a.setPrestadorNome(prestador.getNome());
        if (servico != null) {
            a.setServicoId(servico.getId());
            a.setServicoTitulo(servico.getTitulo());
        }
        a.setStatus(status);
        LocalDateTime criado = LocalDateTime.now().minusDays(diasAtras).withHour(10).withMinute(0).withSecond(0).withNano(0);
        a.setCriadoEm(criado);
        if (status != Atendimento.Status.CONTATO_SOLICITADO) a.setConfirmadoEm(criado.plusHours(5));
        if (status == Atendimento.Status.CONCLUIDO) {
            a.setDataAgendada(criado.toLocalDate().plusDays(2));
            a.setHorarioAgendado(LocalTime.of(8, 30));
            a.setConcluidoEm(criado.plusDays(Math.min(3, Math.max(0, diasAtras - 1))));
        }
        return atendimentoRepository.save(a);
    }

    private void avaliarPrestador(Atendimento a, Prestador p, Cliente c, int nota, String comentario, int diasAtras) {
        Servico servico = a.getServicoId() == null ? null : servicoRepository.findById(a.getServicoId()).orElse(null);
        AvaliacaoPrestador av = avaliacaoPrestadorRepository.save(AvaliacaoPrestador.builder()
                .atendimentoId(a.getId()).prestador(p).cliente(c).servico(servico)
                .qualidade(nota).prazo(Math.max(1, nota - (nota == 5 ? 0 : 1))).preco(nota).organizacao(nota).atendimento(nota)
                .comentario(comentario).build());
        recuar("avaliacoes_prestadores", av.getId(), Math.max(0, diasAtras));
    }

    private void avaliarCliente(Atendimento a, int nota, String comentario, int diasAtras) {
        AvaliacaoCliente av = new AvaliacaoCliente();
        av.setAtendimentoId(a.getId());
        av.setClienteUsuarioId(a.getClienteUsuarioId());
        av.setPrestadorUsuarioId(a.getPrestadorUsuarioId());
        av.setComunicacao(nota);
        av.setPontualidade(nota);
        av.setCumprimentoCombinado(nota);
        av.setComentario(comentario);
        av.setCriadoEm(LocalDateTime.now().minusDays(Math.max(0, diasAtras)));
        avaliacaoClienteRepository.save(av);
    }

    // ---------- Favoritos, agenda, certificações, notificações e termos ----------

    private void semearFavoritos(Cliente clienteTeste, Cliente clienteAmbos, List<Cliente> clientes, List<Prestador> prestadores) {
        List<Favorito> favoritos = new ArrayList<>(List.of(
                new Favorito(clienteTeste.getId(), prestadores.get(0).getId()),
                new Favorito(clienteTeste.getId(), prestadores.get(4).getId()),
                new Favorito(clienteTeste.getId(), prestadores.get(7).getId()),
                new Favorito(clienteAmbos.getId(), prestadores.get(0).getId())));
        for (int i = 0; i < clientes.size(); i++) {
            favoritos.add(new Favorito(clientes.get(i).getId(), prestadores.get(i % 3 == 0 ? 0 : 2 + i).getId()));
        }
        favoritoRepository.saveAll(favoritos);
    }

    private void semearDisponibilidade(List<Prestador> prestadores) {
        List<Disponibilidade> agenda = new ArrayList<>();
        for (int i = 0; i < prestadores.size(); i++) {
            Long id = prestadores.get(i).getId();
            for (Disponibilidade.DiaSemana dia : List.of(Disponibilidade.DiaSemana.SEGUNDA, Disponibilidade.DiaSemana.TERCA,
                    Disponibilidade.DiaSemana.QUARTA, Disponibilidade.DiaSemana.QUINTA, Disponibilidade.DiaSemana.SEXTA)) {
                agenda.add(new Disponibilidade(id, dia, LocalTime.of(i % 2 == 0 ? 8 : 9, 0), LocalTime.of(i % 2 == 0 ? 17 : 18, 0)));
            }
            if (i % 3 != 2) agenda.add(new Disponibilidade(id, Disponibilidade.DiaSemana.SABADO, LocalTime.of(8, 0), LocalTime.of(12, 0)));
        }
        disponibilidadeRepository.saveAll(agenda);
    }

    private void semearCertificacoes(Prestador prestadorTeste, List<Prestador> prestadores) {
        List<Certificacao> lista = new ArrayList<>();
        lista.add(certificacao(prestadorTeste, "NR-10 Segurança em Instalações Elétricas", "SENAI-SP", 400, 330, "NR10-2025-1187", Certificacao.Estado.APROVADA, null));
        lista.add(certificacao(prestadorTeste, "Instalador de Sistemas Fotovoltaicos", "Instituto Solar Brasil", 30, null, null, Certificacao.Estado.PENDENTE, null));
        lista.add(certificacao(prestadorTeste, "Curso de Automação Residencial", "Escola Técnica Fictícia", 700, null, null, Certificacao.Estado.REPROVADA,
                "O documento enviado está ilegível. Envie uma digitalização com melhor qualidade."));
        lista.add(certificacao(prestadores.get(3), "Técnico em Edificações - CREA", "CREA-SP", 2000, null, "5061234567", Certificacao.Estado.APROVADA, null));
        lista.add(certificacao(prestadores.get(6), "NR-35 Trabalho em Altura", "SENAI-SP", 200, 530, "NR35-2026-0042", Certificacao.Estado.PENDENTE, null));
        certificacaoRepository.saveAll(lista);
    }

    private Certificacao certificacao(Prestador p, String nome, String emissor, int emitidoHaDias, Integer validoPorDias,
                                      String registro, Certificacao.Estado estado, String observacao) {
        Certificacao c = new Certificacao();
        c.setPrestadorId(p.getId());
        c.setNome(nome);
        c.setEmissor(emissor);
        c.setDataEmissao(LocalDate.now().minusDays(emitidoHaDias));
        if (validoPorDias != null) c.setDataValidade(LocalDate.now().plusDays(validoPorDias));
        c.setCodigoRegistro(registro);
        c.setDocumentoChave(documentoDemonstracao(nome));
        c.setDocumentoNome("certificado-demonstracao.pdf");
        c.setEstado(estado);
        c.setObservacaoValidacao(observacao);
        return c;
    }

    /** Grava um PDF mínimo de demonstração na pasta de uploads e devolve a chave. */
    private String documentoDemonstracao(String titulo) {
        String texto = "Documento ficticio de demonstracao - " + titulo.replaceAll("[^A-Za-z0-9 .-]", "");
        String conteudo = "BT /F1 14 Tf 40 760 Td (" + texto + ") Tj ET";
        String pdf = "%PDF-1.4\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n"
                + "2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj\n"
                + "3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 612 792]/Contents 4 0 R/Resources<</Font<</F1 5 0 R>>>>>>endobj\n"
                + "4 0 obj<</Length " + conteudo.length() + ">>stream\n" + conteudo + "\nendstream endobj\n"
                + "5 0 obj<</Type/Font/Subtype/Type1/BaseFont/Helvetica>>endobj\ntrailer<</Root 1 0 R>>\n%%EOF\n";
        String chave = UUID.randomUUID() + ".pdf";
        try {
            Path pasta = Path.of(uploadDir).toAbsolutePath().normalize();
            Files.createDirectories(pasta);
            Files.writeString(pasta.resolve(chave), pdf, StandardCharsets.ISO_8859_1);
        } catch (Exception e) {
            log.warn("Não foi possível gravar o documento de demonstração: {}", e.getClass().getSimpleName());
        }
        return chave;
    }

    private void semearNotificacoes(Usuario uCliente, Usuario uPrestador, Usuario uAmbos, Prestador prestadorTeste) {
        String perfilTeste = "/prestadores/" + prestadorTeste.getId();
        notificacao(uPrestador, "Beatriz Almeida solicitou contato para um serviço.", "/dashboard", false, 0);
        notificacao(uPrestador, "Gustavo Ferreira solicitou contato para um serviço.", "/dashboard", false, 2);
        notificacao(uPrestador, "Sua certificação NR-10 foi aprovada.", "/prestadores/minhas-certificacoes", true, 30);
        notificacao(uPrestador, "Você recebeu uma nova avaliação de 5 estrelas.", "/dashboard", true, 55);
        notificacao(uCliente, "Rafael Teste Elétrica confirmou o seu atendimento.", perfilTeste, false, 4);
        notificacao(uCliente, "Seu atendimento foi concluído. Que tal avaliar o profissional?", perfilTeste, false, 12);
        notificacao(uAmbos, "Bem-vindo ao ObraLink! Complete seu perfil profissional.", "/clientes", true, 150);
    }

    private void notificacao(Usuario u, String mensagem, String link, boolean lida, int diasAtras) {
        Notificacao n = notificacaoRepository.save(Notificacao.builder().usuarioId(u.getId()).mensagem(mensagem).link(link).lida(lida).build());
        if (diasAtras > 0) recuar("notificacoes", n.getId(), diasAtras);
    }

    /** Termos aceitos para todas as contas, menos a do cliente "igor" (serve para testar a tela de aceite). */
    private void aceitarTermos() {
        List<AceiteTermos> aceites = new ArrayList<>();
        for (Usuario u : usuarioRepository.findAll()) {
            if (u.getEmail().startsWith("igor.")) continue;
            AceiteTermos a = new AceiteTermos(u.getId(), TermosService.VERSAO_ATUAL);
            aceites.add(a);
        }
        aceiteTermosRepository.saveAll(aceites);
    }

    // ---------- Utilitários ----------

    /** Ajusta criado_em para o passado, para o painel ter histórico de vários meses. */
    private void recuar(String tabela, Long id, int diasAtras) {
        if (diasAtras <= 0) return;
        jdbc.update("update " + tabela + " set criado_em = ? where id = ?", LocalDateTime.now().minusDays(diasAtras), id);
    }

    private static String telefone(int i) {
        return DadosCadastrais.telefone(String.format("1198%03d%04d", 100 + i, 1000 + i * 37), true);
    }

}
