package com.obraslink.service;

import com.obraslink.model.Certificacao;
import com.obraslink.model.PapelUsuario;
import com.obraslink.dto.CadastroCertificacaoForm;
import com.obraslink.repository.CertificacaoRepository;
import com.obraslink.repository.PrestadorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CertificacaoService {
    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;

    private final AcessoService acesso;
    private final PrestadorRepository prestadores;
    private final CertificacaoRepository certificacoes;
    private final PerfilAtivoService perfil;

    @Value("${obralink.upload-dir:./uploads}")
    private String uploadDir;

    @Transactional(readOnly = true)
    public List<Certificacao> minhas() {
        return certificacoes.findByPrestadorIdOrderByCriadoEmDesc(prestadorAtual());
    }

    @Transactional(readOnly = true)
    public Certificacao minhaParaEdicao(Long id) {
        Certificacao c = minhaCertificacao(id);
        garantirEditavel(c);
        return c;
    }

    @Transactional(readOnly = true)
    public List<Certificacao> pendentes() {
        var u = acesso.getUsuarioAtualOrThrow();
        if (!Boolean.TRUE.equals(u.getAtivo()) || !u.getPapeis().contains(PapelUsuario.ADMIN)) {
            throw new IllegalArgumentException("Apenas administradores podem revisar certificações.");
        }
        return certificacoes.findByEstadoOrderByCriadoEmAsc(Certificacao.Estado.PENDENTE);
    }

    @Transactional
    public void criar(String nome, String emissor, LocalDate emissao, LocalDate validade,
                      String codigo, MultipartFile arquivo) {
        long prestadorId = prestadorAtual();
        criarParaPrestador(prestadorId, nome, emissor, emissao, validade, codigo, arquivo);
    }

    @Transactional
    public void criarParaPrestador(Long prestadorId, String nome, String emissor, LocalDate emissao,
                                   LocalDate validade, String codigo, MultipartFile arquivo) {
        validarCampos(nome, emissor, emissao, validade, codigo);
        String documentoChave = armazenarDocumento(arquivo, true);
        Certificacao c = new Certificacao();
        preencher(c, nome, emissor, emissao, validade, codigo);
        c.setPrestadorId(prestadorId);
        c.setDocumentoChave(documentoChave);
        c.setDocumentoNome(safeName(arquivo.getOriginalFilename()));
        c.setEstado(Certificacao.Estado.PENDENTE);
        certificacoes.save(c);
    }

    @Transactional
    public void registrarNoCadastro(Long prestadorId, CadastroCertificacaoForm form) {
        if (form == null || form.isVazio()) return;
        criarParaPrestador(prestadorId, form.getNome(), form.getEmissor(),
                dataCadastro(form.getDataEmissao(), true), dataCadastro(form.getDataValidade(), false),
                form.getCodigoRegistro(), form.getDocumento());
    }

    @Transactional
    public void editar(Long id, String nome, String emissor, LocalDate emissao, LocalDate validade,
                       String codigo, MultipartFile arquivo) {
        Certificacao c = minhaCertificacao(id);
        garantirEditavel(c);
        validarCampos(nome, emissor, emissao, validade, codigo);
        preencher(c, nome, emissor, emissao, validade, codigo);
        if (arquivo != null && !arquivo.isEmpty()) {
            String documentoChave = armazenarDocumento(arquivo, true);
            c.setDocumentoChave(documentoChave);
            c.setDocumentoNome(safeName(arquivo.getOriginalFilename()));
        }
        // Uma alteração do titular sempre volta para a fila de análise.
        c.setEstado(Certificacao.Estado.PENDENTE);
        c.setObservacaoValidacao(null);
    }

    @Transactional(readOnly = true)
    public Certificacao consultarDocumento(Long id) {
        var u = acesso.getUsuarioAtualOrThrow();
        var c = certificacoes.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Certificação não encontrada."));
        boolean admin = Boolean.TRUE.equals(u.getAtivo()) && u.getPapeis().contains(PapelUsuario.ADMIN);
        boolean titular = Boolean.TRUE.equals(u.getAtivo())
                && prestadores.findById(c.getPrestadorId())
                .map(p -> u.getId().equals(p.getUsuarioId())).orElse(false);
        if (!admin && !titular) throw new IllegalArgumentException("Sem acesso ao documento.");
        return c;
    }

    @Transactional
    public void validar(Long id, Certificacao.Estado estado, String observacao) {
        var u = acesso.getUsuarioAtualOrThrow();
        if (!u.getPapeis().contains(PapelUsuario.ADMIN) || !Boolean.TRUE.equals(u.getAtivo())) {
            throw new IllegalArgumentException("Apenas administradores ativos podem validar certificações.");
        }
        if (estado == null || estado == Certificacao.Estado.PENDENTE) {
            throw new IllegalArgumentException("Selecione aprovada ou reprovada.");
        }
        var c = certificacoes.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Certificação não encontrada."));
        if (c.getEstado() != Certificacao.Estado.PENDENTE) {
            throw new IllegalArgumentException("Somente certificações pendentes podem ser analisadas.");
        }
        if (estado == Certificacao.Estado.REPROVADA && (observacao == null || observacao.isBlank())) {
            throw new IllegalArgumentException("Informe o motivo da reprovação.");
        }
        if (observacao != null && observacao.length() > 1000) {
            throw new IllegalArgumentException("A observação deve ter até 1.000 caracteres.");
        }
        c.setEstado(estado);
        c.setObservacaoValidacao(observacao == null ? null : observacao.trim());
    }

    /**
     * Exclusão de conta: apaga do disco os comprovantes das certificações do prestador, só depois que a exclusão
     * for confirmada no banco (se a transação for desfeita, os arquivos continuam lá).
     */
    public void excluirDocumentosDoPrestadorAposConfirmar(Long prestadorId) {
        Path root = Path.of(uploadDir).toAbsolutePath().normalize();
        List<Path> arquivos = certificacoes.findByPrestadorIdOrderByCriadoEmDesc(prestadorId).stream()
                .map(Certificacao::getDocumentoChave).filter(java.util.Objects::nonNull)
                .map(chave -> root.resolve(chave).normalize()).filter(p -> p.startsWith(root)).toList();
        if (arquivos.isEmpty()) return;
        Runnable apagar = () -> arquivos.forEach(p -> {
            try { Files.deleteIfExists(p); } catch (IOException e) { /* arquivo já removido ou indisponível: segue */ }
        });
        if (!org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) { apagar.run(); return; }
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override public void afterCommit() { apagar.run(); }
                });
    }

    public Path arquivo(Certificacao c) {
        Path root = Path.of(uploadDir).toAbsolutePath().normalize();
        Path file = root.resolve(c.getDocumentoChave()).normalize();
        if (!file.startsWith(root)) throw new IllegalArgumentException("Documento inválido.");
        return file;
    }

    private Certificacao minhaCertificacao(Long id) {
        Long prestadorId = prestadorAtual();
        return certificacoes.findByIdAndPrestadorId(id, prestadorId)
                .orElseThrow(() -> new IllegalArgumentException("Certificação não encontrada para este prestador."));
    }

    private void garantirEditavel(Certificacao c) {
        if (c.getEstado() == Certificacao.Estado.APROVADA) {
            throw new IllegalArgumentException("Certificações aprovadas não podem ser editadas.");
        }
    }

    private void preencher(Certificacao c, String nome, String emissor, LocalDate emissao,
                           LocalDate validade, String codigo) {
        c.setNome(nome.trim());
        c.setEmissor(emissor.trim());
        c.setDataEmissao(emissao);
        c.setDataValidade(validade);
        c.setCodigoRegistro(codigo == null || codigo.isBlank() ? null : codigo.trim());
    }

    private void validarCampos(String nome, String emissor, LocalDate emissao,
                               LocalDate validade, String codigo) {
        if (nome == null || nome.isBlank() || nome.trim().length() > 180
                || emissor == null || emissor.isBlank() || emissor.trim().length() > 180
                || emissao == null || emissao.isAfter(LocalDate.now())
                || validade != null && validade.isBefore(emissao)
                || codigo != null && codigo.trim().length() > 100) {
            throw new IllegalArgumentException("Confira nome, emissor, código e datas da certificação.");
        }
    }

    private LocalDate dataCadastro(String valor, boolean obrigatoria) {
        if (valor == null || valor.isBlank()) {
            if (obrigatoria) throw new IllegalArgumentException("Informe a data de emissão da certificação.");
            return null;
        }
        try {
            return LocalDate.parse(valor);
        } catch (java.time.format.DateTimeParseException e) {
            throw new IllegalArgumentException("Informe uma data válida para a certificação.");
        }
    }

    private String armazenarDocumento(MultipartFile arquivo, boolean obrigatorio) {
        if (arquivo == null || arquivo.isEmpty()) {
            if (obrigatorio) throw new IllegalArgumentException("Anexe o comprovante da certificação (PDF, JPG, PNG ou WebP de até 10 MB).");
            return null;
        }
        String ext = validarDocumento(arquivo);
        String key = UUID.randomUUID() + ext;
        try {
            Path root = Path.of(uploadDir).toAbsolutePath().normalize();
            Files.createDirectories(root);
            Path target = root.resolve(key).normalize();
            if (!target.startsWith(root)) throw new IllegalArgumentException("Arquivo inválido.");
            arquivo.transferTo(target);
            return key;
        } catch (IOException e) {
            throw new IllegalArgumentException("Não foi possível armazenar o documento.", e);
        }
    }

    private long prestadorAtual() {
        var u = acesso.getUsuarioAtualOrThrow();
        if (!Boolean.TRUE.equals(u.getAtivo()) || !u.getPapeis().contains(PapelUsuario.PRESTADOR)
                || perfil.atual() != PapelUsuario.PRESTADOR) {
            throw new IllegalArgumentException("Ative o perfil de prestador para gerenciar certificações.");
        }
        return prestadores.findByUsuarioId(u.getId()).map(p -> p.getId())
                .orElseThrow(() -> new IllegalArgumentException("Cadastro profissional não encontrado."));
    }

    private String safeName(String name) {
        if (name == null || name.isBlank()) return "documento";
        String leaf = name.replace('\\', '/');
        leaf = leaf.substring(leaf.lastIndexOf('/') + 1).replaceAll("[^A-Za-z0-9._-]", "_");
        return leaf.isBlank() ? "documento" : leaf;
    }

    /**
     * Confere o comprovante pelo CONTEÚDO do arquivo (não pelo tipo informado pelo navegador nem pela extensão),
     * com mensagens separadas para tamanho e formato. Devolve a extensão usada no disco.
     */
    String validarDocumento(MultipartFile arquivo) {
        String nome = safeName(arquivo.getOriginalFilename());
        if (arquivo.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException(String.format(java.util.Locale.forLanguageTag("pt-BR"),
                    "O arquivo %s tem %.1f MB. O limite é 10 MB.", nome, arquivo.getSize() / (1024.0 * 1024.0)));
        }
        String ext = extensaoPeloConteudo(arquivo);
        if (ext == null) throw new IllegalArgumentException("O arquivo " + nome + " não é um PDF, JPG, PNG ou WebP válido.");
        return ext;
    }

    /** ".pdf", ".jpg", ".png" ou ".webp" conforme a assinatura do arquivo; null se não for nenhum desses. */
    static String extensaoPeloConteudo(MultipartFile file) {
        byte[] b;
        try (var in = file.getInputStream()) {
            b = in.readNBytes(12);
        } catch (IOException e) {
            return null;
        }
        if (b.length >= 5 && b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F' && b[4] == '-') return ".pdf";
        if (b.length >= 3 && (b[0] & 255) == 0xFF && (b[1] & 255) == 0xD8 && (b[2] & 255) == 0xFF) return ".jpg";
        if (b.length >= 8 && (b[0] & 255) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
                && b[4] == 0x0D && b[5] == 0x0A && b[6] == 0x1A && b[7] == 0x0A) return ".png";
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') return ".webp";
        return null;
    }
}
