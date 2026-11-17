package com.obraslink.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Armazenamento das imagens de referência anexadas a uma solicitação de orçamento. Mesmo critério de
 * validação (tamanho, assinatura do arquivo) do {@link ImagemService}, numa pasta própria, já que não
 * está ligado a prestador/serviço algum.
 */
@Slf4j
@Service
public class OrcamentoAnexoService {
    public static final long TAMANHO_MAXIMO = 5L * 1024 * 1024;
    public static final int MAX_ANEXOS_POR_SOLICITACAO = 6;
    private static final Pattern CHAVE_VALIDA = Pattern.compile("^[a-f0-9]{32}\\.(jpg|png|webp)$");

    private final Path pasta;

    public OrcamentoAnexoService(@Value("${obralink.upload-dir:./uploads}") String uploadDir) {
        this.pasta = Path.of(uploadDir).toAbsolutePath().normalize().resolve("orcamentos");
    }

    public String armazenar(MultipartFile arquivo) {
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
            throw new IllegalArgumentException("Não foi possível salvar o anexo. Tente novamente.", e);
        }
    }

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

    public void excluirArquivo(String chave) {
        arquivo(chave).ifPresent(caminho -> {
            try {
                Files.deleteIfExists(caminho);
            } catch (IOException e) {
                log.warn("Não foi possível apagar o anexo {}", chave);
            }
        });
    }

    String validar(MultipartFile arquivo) {
        if (arquivo == null || arquivo.isEmpty()) throw new IllegalArgumentException("Selecione um arquivo.");
        if (arquivo.getSize() > TAMANHO_MAXIMO) throw new IllegalArgumentException("Cada anexo deve ter no máximo 5 MB.");
        byte[] b = new byte[12];
        int lidos;
        try (InputStream in = arquivo.getInputStream()) {
            lidos = in.readNBytes(b, 0, b.length);
        } catch (IOException e) {
            throw new IllegalArgumentException("Não foi possível ler o anexo.", e);
        }
        if (lidos >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) return "jpg";
        if (lidos >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
                && b[4] == 0x0D && b[5] == 0x0A && b[6] == 0x1A && b[7] == 0x0A) return "png";
        if (lidos >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') return "webp";
        throw new IllegalArgumentException("Envie uma imagem JPG, PNG ou WebP.");
    }
}
