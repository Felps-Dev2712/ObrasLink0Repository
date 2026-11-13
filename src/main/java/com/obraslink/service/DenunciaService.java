package com.obraslink.service;

import com.obraslink.dto.DenunciaForm;
import com.obraslink.model.Atendimento;
import com.obraslink.model.Denuncia;
import com.obraslink.model.PapelUsuario;
import com.obraslink.model.Usuario;
import com.obraslink.repository.AtendimentoRepository;
import com.obraslink.repository.DenunciaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Denúncia de um participante contra o outro num atendimento; ver {@link Denuncia}. */
@Service
@RequiredArgsConstructor
public class DenunciaService {
    private final AtendimentoRepository atendimentos;
    private final DenunciaRepository denuncias;
    private final AcessoService acesso;

    public record Detalhes(Atendimento atendimento, Denuncia denuncia, boolean podeDenunciar) {}

    /** Atendimentos (entre os informados) em que o usuário atual já registrou uma denúncia. */
    @Transactional(readOnly = true)
    public Set<Long> atendimentosJaDenunciados(Collection<Long> atendimentoIds, Long autorUsuarioId) {
        if (atendimentoIds == null || atendimentoIds.isEmpty()) return Set.of();
        return new HashSet<>(denuncias.findAtendimentoIdsDenunciados(atendimentoIds, autorUsuarioId));
    }

    @Transactional(readOnly = true)
    public Detalhes consultar(Long atendimentoId) {
        Usuario usuario = acesso.getUsuarioAtualOrThrow();
        Atendimento atendimento = atendimentos.findById(atendimentoId)
                .orElseThrow(() -> new IllegalArgumentException("Atendimento não encontrado."));
        validarParticipante(atendimento, usuario);
        Denuncia existente = denuncias.findByAtendimentoIdAndAutorUsuarioId(atendimentoId, usuario.getId()).orElse(null);
        return new Detalhes(atendimento, existente, existente == null);
    }

    @Transactional
    public void denunciar(Long atendimentoId, DenunciaForm form) {
        Usuario usuario = acesso.getUsuarioAtualOrThrow();
        Atendimento atendimento = atendimentos.findById(atendimentoId)
                .orElseThrow(() -> new IllegalArgumentException("Atendimento não encontrado."));
        validarParticipante(atendimento, usuario);
        if (denuncias.findByAtendimentoIdAndAutorUsuarioId(atendimentoId, usuario.getId()).isPresent()) {
            throw new IllegalArgumentException("Você já denunciou este atendimento.");
        }
        if (form.getMotivo() == null) {
            throw new IllegalArgumentException("Selecione o motivo da denúncia.");
        }
        String descricao = com.obraslink.validation.Comentario.normalizar(form.getDescricao());
        if (descricao == null) {
            throw new IllegalArgumentException("Descreva o que aconteceu.");
        }

        boolean souCliente = usuario.getId().equals(atendimento.getClienteUsuarioId());
        Denuncia denuncia = Denuncia.builder()
                .atendimentoId(atendimentoId)
                .autorUsuarioId(usuario.getId())
                .denunciadoUsuarioId(souCliente ? atendimento.getPrestadorUsuarioId() : atendimento.getClienteUsuarioId())
                .autorNome(souCliente ? atendimento.getClienteNome() : atendimento.getPrestadorNome())
                .denunciadoNome(souCliente ? atendimento.getPrestadorNome() : atendimento.getClienteNome())
                .motivo(form.getMotivo())
                .descricao(descricao)
                .estado(Denuncia.Estado.PENDENTE)
                .criadoEm(LocalDateTime.now())
                .build();
        denuncias.save(denuncia);
    }

    @Transactional(readOnly = true)
    public List<Denuncia> pendentes() {
        var u = acesso.getUsuarioAtualOrThrow();
        if (!Boolean.TRUE.equals(u.getAtivo()) || !u.getPapeis().contains(PapelUsuario.ADMIN)) {
            throw new IllegalArgumentException("Apenas administradores podem revisar denúncias.");
        }
        return denuncias.findByEstadoOrderByCriadoEmAsc(Denuncia.Estado.PENDENTE);
    }

    @Transactional
    public void analisar(Long id, Denuncia.Estado estado, String observacao) {
        var u = acesso.getUsuarioAtualOrThrow();
        if (!u.getPapeis().contains(PapelUsuario.ADMIN) || !Boolean.TRUE.equals(u.getAtivo())) {
            throw new IllegalArgumentException("Apenas administradores ativos podem analisar denúncias.");
        }
        if (estado == null || estado == Denuncia.Estado.PENDENTE) {
            throw new IllegalArgumentException("Selecione procedente ou improcedente.");
        }
        var d = denuncias.findById(id).orElseThrow(() -> new IllegalArgumentException("Denúncia não encontrada."));
        if (d.getEstado() != Denuncia.Estado.PENDENTE) {
            throw new IllegalArgumentException("Somente denúncias pendentes podem ser analisadas.");
        }
        if (observacao != null && observacao.length() > 1000) {
            throw new IllegalArgumentException("A observação deve ter até 1.000 caracteres.");
        }
        d.setEstado(estado);
        d.setObservacaoAnalise(observacao == null || observacao.isBlank() ? null : observacao.trim());
        d.setAnalisadoEm(LocalDateTime.now());
    }

    private void validarParticipante(Atendimento atendimento, Usuario usuario) {
        if (!Boolean.TRUE.equals(usuario.getAtivo())
                || (!usuario.getId().equals(atendimento.getClienteUsuarioId())
                && !usuario.getId().equals(atendimento.getPrestadorUsuarioId()))) {
            throw new IllegalArgumentException("Você não tem acesso a este atendimento.");
        }
    }
}
