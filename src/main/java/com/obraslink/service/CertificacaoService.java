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
    private static final Set<String> ALLOWED_TYPES = Set.of("application/pdf", "image/jpeg", "image/png");

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
            if (obrigatorio) throw new IllegalArgumentException("Anexe um documento de até 10 MB.");
            return null;
        }
        String type = arquivo.getContentType();
        if (arquivo.getSize() > MAX_FILE_SIZE || type == null || !ALLOWED_TYPES.contains(type)
                || !signatureMatches(arquivo, type)) {
            throw new IllegalArgumentException("Envie um documento PDF, JPG ou PNG válido de até 10 MB.");
        }
        String ext = switch (type) {
            case "application/pdf" -> ".pdf";
            case "image/jpeg" -> ".jpg";
            default -> ".png";
        };
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

    private boolean signatureMatches(MultipartFile file, String type) {
        try {
            byte[] b = file.getBytes();
            if (type.equals("application/pdf")) return b.length >= 5 && b[0] == '%' && b[1] == 'P'
                    && b[2] == 'D' && b[3] == 'F' && b[4] == '-';
            if (type.equals("image/png")) return b.length >= 8 && (b[0] & 255) == 137
                    && b[1] == 'P' && b[2] == 'N' && b[3] == 'G';
            return b.length >= 3 && (b[0] & 255) == 255 && (b[1] & 255) == 216 && (b[2] & 255) == 255;
        } catch (IOException e) {
            return false;
        }
    }
}
