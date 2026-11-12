package com.obraslink.service;

import com.obraslink.model.PapelUsuario;
import com.obraslink.model.Prestador;
import com.obraslink.model.ServicoFoto;
import com.obraslink.repository.PrestadorRepository;
import com.obraslink.repository.ServicoFotoRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Fotos de perfil, capa (banner) do prestador e fotos dos serviços.
 * O tipo é conferido pela assinatura do arquivo (não pela extensão), o nome no disco é aleatório
 * e só o dono (ou um administrador) altera as imagens.
 */
@Slf4j
@Service
public class ImagemService {
    public static final long TAMANHO_MAXIMO = 5L * 1024 * 1024;
    public static final int MAX_FOTOS_POR_SERVICO = 6;
    private static final Pattern CHAVE_VALIDA = Pattern.compile("^[a-f0-9]{32}\\.(jpg|png|webp)$");

    private final AcessoService acesso;
    private final PrestadorRepository prestadores;
    private final ServicoFotoRepository fotos;
    private final PerfilAtivoService perfil;
    private final com.obraslink.repository.ClienteRepository clientes;
    private final Path pasta;

    @Autowired
    public ImagemService(AcessoService acesso, PrestadorRepository prestadores, ServicoFotoRepository fotos,
                         PerfilAtivoService perfil, com.obraslink.repository.ClienteRepository clientes,
                         @Value("${obralink.upload-dir:./uploads}") String uploadDir) {
        this.acesso = acesso;
        this.prestadores = prestadores;
        this.fotos = fotos;
        this.perfil = perfil;
        this.clientes = clientes;
        this.pasta = Path.of(uploadDir).toAbsolutePath().normalize().resolve("imagens");
    }

    public ImagemService(AcessoService acesso, PrestadorRepository prestadores, ServicoFotoRepository fotos,
                         PerfilAtivoService perfil, String uploadDir) {
        this(acesso, prestadores, fotos, perfil, null, uploadDir);
    }

    // ---------- Foto do cliente ----------

    @Transactional
    public void atualizarFotoCliente(MultipartFile arquivo) {
        com.obraslink.model.Cliente cliente = clienteDoUsuarioAtual();
        String nova = armazenar(arquivo);
        String antiga = cliente.getFotoPerfilChave();
        cliente.setFotoPerfilChave(nova);
        aposConfirmar(() -> excluirArquivo(antiga), () -> excluirArquivo(nova));
    }

    @Transactional
    public void removerFotoCliente() {
        com.obraslink.model.Cliente cliente = clienteDoUsuarioAtual();
        String antiga = cliente.getFotoPerfilChave();
        cliente.setFotoPerfilChave(null);
        aposConfirmar(() -> excluirArquivo(antiga), null);
    }

    private com.obraslink.model.Cliente clienteDoUsuarioAtual() {
        var usuario = acesso.getUsuarioAtualOrThrow();
        if (clientes == null || !Boolean.TRUE.equals(usuario.getAtivo()) || !usuario.getPapeis().contains(PapelUsuario.CLIENTE)
                || perfil.atual() != PapelUsuario.CLIENTE) {
            throw new IllegalArgumentException("Ative o perfil de cliente para alterar sua foto.");
        }
        return clientes.findByUsuarioId(usuario.getId())
                .orElseThrow(() -> new IllegalArgumentException("Cadastro de cliente não encontrado."));
    }

    public enum Tipo { FOTO_PERFIL, BANNER }

    // ---------- Perfil do prestador ----------

    /** Capa: exibida em 4:1, até 1200 × 300 px. Abaixo disso a imagem seria ampliada na tela e perderia qualidade. */
    public static final int CAPA_LARGURA_MINIMA = 1200;
    public static final int CAPA_ALTURA_MINIMA = 300;

    @Transactional
    public void atualizarImagemPerfil(Tipo tipo, MultipartFile arquivo) {
        Prestador prestador = prestadorDoUsuarioAtual();
        if (tipo == Tipo.BANNER) validarCapa(arquivo);
        String nova = armazenar(arquivo);
        String antiga = tipo == Tipo.FOTO_PERFIL ? prestador.getFotoPerfilChave() : prestador.getBannerChave();
        if (tipo == Tipo.FOTO_PERFIL) prestador.setFotoPerfilChave(nova);
        else prestador.setBannerChave(nova);
        aposConfirmar(() -> excluirArquivo(antiga), () -> excluirArquivo(nova));
    }

    @Transactional
    public void removerImagemPerfil(Tipo tipo) {
        Prestador prestador = prestadorDoUsuarioAtual();
        String antiga = tipo == Tipo.FOTO_PERFIL ? prestador.getFotoPerfilChave() : prestador.getBannerChave();
        if (tipo == Tipo.FOTO_PERFIL) prestador.setFotoPerfilChave(null);
        else prestador.setBannerChave(null);
        aposConfirmar(() -> excluirArquivo(antiga), null);
    }

    // ---------- Fotos dos serviços ----------

    @Transactional
    public int adicionarFotosServico(Long servicoId, List<MultipartFile> arquivos) {
        List<MultipartFile> validos = arquivos == null ? List.of()
                : arquivos.stream().filter(a -> a != null && !a.isEmpty()).toList();
        if (validos.isEmpty()) return 0;
        exigirDonoDoServico(servicoId);
        long atuais = fotos.countByServicoId(servicoId);
        if (atuais + validos.size() > MAX_FOTOS_POR_SERVICO) {
            throw new IllegalArgumentException("Cada serviço pode ter até " + MAX_FOTOS_POR_SERVICO + " fotos. Hoje ele tem " + atuais + ".");
        }
        // Valida todos antes de gravar qualquer um, para não salvar metade do envio.
        validos.forEach(this::validar);
        List<ServicoFoto> novas = new ArrayList<>();
        try {
            for (MultipartFile arquivo : validos) novas.add(new ServicoFoto(servicoId, armazenar(arquivo)));
            fotos.saveAll(novas);
        } catch (RuntimeException e) {
            novas.forEach(f -> excluirArquivo(f.getChave()));
            throw e;
        }
        aposConfirmar(null, () -> novas.forEach(f -> excluirArquivo(f.getChave())));
        return novas.size();
    }

    @Transactional
    public void excluirFotoServico(Long servicoId, Long fotoId) {
        exigirDonoDoServico(servicoId);
        ServicoFoto foto = fotos.findByIdAndServicoId(fotoId, servicoId)
                .orElseThrow(() -> new IllegalArgumentException("Foto não encontrada para este serviço."));
        fotos.delete(foto);
        aposConfirmar(() -> excluirArquivo(foto.getChave()), null);
    }

    @Transactional(readOnly = true)
    public List<ServicoFoto> fotosDoServico(Long servicoId) {
        return fotos.findByServicoIdOrderByIdAsc(servicoId);
    }

    /** Fotos de vários serviços numa consulta, agrupadas por serviço e na ordem de envio. */
    @Transactional(readOnly = true)
    public Map<Long, List<ServicoFoto>> fotosPorServico(Collection<Long> servicoIds) {
        if (servicoIds == null || servicoIds.isEmpty()) return Map.of();
        return fotos.findByServicoIdInOrderByIdAsc(servicoIds).stream()
                .collect(Collectors.groupingBy(ServicoFoto::getServicoId, LinkedHashMap::new, Collectors.toList()));
    }

    // ---------- Arquivos ----------

    /** Caminho do arquivo para exibição; vazio se a chave for inválida ou o arquivo não existir. */
    public Optional<Path> arquivo(String chave) {
        if (chave == null || !CHAVE_VALIDA.matcher(chave).matches()) return Optional.empty();
        Path arquivo = pasta.resolve(chave).normalize();
        if (!arquivo.startsWith(pasta) || !Files.isRegularFile(arquivo)) return Optional.empty();
        return Optional.of(arquivo);
    }

    public static String tipoMime(String chave) {
        if (chave.endsWith(".png")) return "image/png";
        if (chave.endsWith(".webp")) return "image/webp";
        return "image/jpeg";
    }

    String armazenar(MultipartFile arquivo) {
        String extensao = validar(arquivo);
        String chave = UUID.randomUUID().toString().replace("-", "") + "." + extensao;
        try {
            Files.createDirectories(pasta);
            Path destino = pasta.resolve(chave).normalize();
            if (!destino.startsWith(pasta)) throw new IllegalArgumentException("Arquivo inválido.");
            try (InputStream in = arquivo.getInputStream()) {
                Files.copy(in, destino);
            }
            return chave;
        } catch (IOException e) {
            throw new IllegalArgumentException("Não foi possível salvar a imagem. Tente novamente.", e);
        }
    }

    /** Confere tamanho e assinatura. Devolve a extensão correspondente ao conteúdo real do arquivo. */
    String validar(MultipartFile arquivo) {
        if (arquivo == null || arquivo.isEmpty()) throw new IllegalArgumentException("Selecione uma imagem.");
        if (arquivo.getSize() > TAMANHO_MAXIMO) throw new IllegalArgumentException("A imagem deve ter no máximo 5 MB.");
        byte[] b = new byte[12];
        int lidos;
        try (InputStream in = arquivo.getInputStream()) {
            lidos = in.readNBytes(b, 0, b.length);
        } catch (IOException e) {
            throw new IllegalArgumentException("Não foi possível ler a imagem.", e);
        }
        if (lidos >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) return "jpg";
        if (lidos >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
                && b[4] == 0x0D && b[5] == 0x0A && b[6] == 0x1A && b[7] == 0x0A) return "png";
        if (lidos >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') return "webp";
        throw new IllegalArgumentException("Envie uma imagem JPG, PNG ou WebP.");
    }

    /** A capa precisa ter pelo menos 1200 × 300 px. Não altera nem recomprime o arquivo: só confere as dimensões. */
    void validarCapa(MultipartFile arquivo) {
        String extensao = validar(arquivo);
        int[] tamanho = dimensoes(arquivo, extensao)
                .orElseThrow(() -> new IllegalArgumentException("Não foi possível ler as dimensões da imagem. Envie outra foto."));
        if (tamanho[0] < CAPA_LARGURA_MINIMA || tamanho[1] < CAPA_ALTURA_MINIMA) {
            throw new IllegalArgumentException("A capa deve ter pelo menos " + CAPA_LARGURA_MINIMA + " × " + CAPA_ALTURA_MINIMA
                    + " px (a imagem enviada tem " + tamanho[0] + " × " + tamanho[1] + " px). Use uma foto na horizontal, de preferência 1600 × 400 px.");
        }
    }

    /** Largura e altura lidas só do cabeçalho do arquivo (sem decodificar a imagem inteira). */
    static java.util.Optional<int[]> dimensoes(MultipartFile arquivo, String extensao) {
        try (InputStream in = arquivo.getInputStream()) {
            if ("webp".equals(extensao)) return dimensoesWebp(in.readNBytes(30));
            try (var iis = javax.imageio.ImageIO.createImageInputStream(in)) {
                if (iis == null) return java.util.Optional.empty();
                var leitores = javax.imageio.ImageIO.getImageReaders(iis);
                if (!leitores.hasNext()) return java.util.Optional.empty();
                var leitor = leitores.next();
                try {
                    leitor.setInput(iis, true, true);
                    return java.util.Optional.of(new int[]{leitor.getWidth(0), leitor.getHeight(0)});
                } finally {
                    leitor.dispose();
                }
            }
        } catch (IOException | RuntimeException e) {
            return java.util.Optional.empty();
        }
    }

    /** WebP (o Java não lê nativamente): dimensões dos blocos VP8X, VP8 (com perdas) e VP8L (sem perdas). */
    static java.util.Optional<int[]> dimensoesWebp(byte[] b) {
        if (b == null || b.length < 30) return java.util.Optional.empty();
        String bloco = new String(b, 12, 4, java.nio.charset.StandardCharsets.US_ASCII);
        int largura, altura;
        switch (bloco) {
            case "VP8X" -> {
                largura = 1 + ((b[24] & 0xFF) | (b[25] & 0xFF) << 8 | (b[26] & 0xFF) << 16);
                altura = 1 + ((b[27] & 0xFF) | (b[28] & 0xFF) << 8 | (b[29] & 0xFF) << 16);
            }
            case "VP8 " -> {
                if ((b[23] & 0xFF) != 0x9D || (b[24] & 0xFF) != 0x01 || (b[25] & 0xFF) != 0x2A) return java.util.Optional.empty();
                largura = ((b[26] & 0xFF) | (b[27] & 0xFF) << 8) & 0x3FFF;
                altura = ((b[28] & 0xFF) | (b[29] & 0xFF) << 8) & 0x3FFF;
            }
            case "VP8L" -> {
                if ((b[20] & 0xFF) != 0x2F) return java.util.Optional.empty();
                largura = 1 + ((b[21] & 0xFF) | (b[22] & 0x3F) << 8);
                altura = 1 + (((b[22] & 0xFF) >> 6) | (b[23] & 0xFF) << 2 | (b[24] & 0x0F) << 10);
            }
            default -> { return java.util.Optional.empty(); }
        }
        return largura > 0 && altura > 0 ? java.util.Optional.of(new int[]{largura, altura}) : java.util.Optional.empty();
    }

    void excluirArquivo(String chave) {
        arquivo(chave).ifPresent(caminho -> {
            try {
                Files.deleteIfExists(caminho);
            } catch (IOException e) {
                log.warn("Não foi possível apagar a imagem antiga {}", chave);
            }
        });
    }

    /** Usado na exclusão de conta: apaga os arquivos só depois que a exclusão for confirmada no banco. */
    public void excluirArquivosAposConfirmar(Collection<String> chaves) {
        if (chaves == null || chaves.isEmpty()) return;
        List<String> copia = List.copyOf(chaves);
        aposConfirmar(() -> copia.forEach(this::excluirArquivo), null);
    }

    /**
     * Arquivo em disco não participa da transação: apaga o antigo só depois do commit e,
     * se a transação for desfeita, apaga o novo para não deixar lixo.
     */
    private void aposConfirmar(Runnable aoConfirmar, Runnable aoDesfazer) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            if (aoConfirmar != null) aoConfirmar.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_COMMITTED && aoConfirmar != null) aoConfirmar.run();
                if (status == STATUS_ROLLED_BACK && aoDesfazer != null) aoDesfazer.run();
            }
        });
    }

    // ---------- Permissões ----------

    private Prestador prestadorDoUsuarioAtual() {
        var usuario = acesso.getUsuarioAtualOrThrow();
        if (!Boolean.TRUE.equals(usuario.getAtivo()) || !usuario.getPapeis().contains(PapelUsuario.PRESTADOR)
                || perfil.atual() != PapelUsuario.PRESTADOR) {
            throw new IllegalArgumentException("Ative o perfil de prestador para alterar as fotos do perfil.");
        }
        return prestadores.findByUsuarioId(usuario.getId())
                .orElseThrow(() -> new IllegalArgumentException("Cadastro profissional não encontrado."));
    }

    private void exigirDonoDoServico(Long servicoId) {
        if (servicoId == null || !acesso.podeGerenciarServico(servicoId)) {
            throw new IllegalArgumentException("Você não pode alterar as fotos deste serviço.");
        }
        if (!acesso.isAdmin() && perfil.atual() != PapelUsuario.PRESTADOR) {
            throw new IllegalArgumentException("Ative o perfil de prestador para alterar as fotos do serviço.");
        }
    }
}
