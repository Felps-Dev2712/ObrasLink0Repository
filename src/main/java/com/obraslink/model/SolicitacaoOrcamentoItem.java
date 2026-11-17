package com.obraslink.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Mensagem individual gerada para um dos prestadores selecionados numa {@link SolicitacaoOrcamento}.
 * O cliente pode ajustar o texto na revisão antes de confirmar; cada item é enviado separadamente,
 * sem que um prestador veja a resposta ou a mensagem dos demais.
 */
@Entity
@Table(name = "solicitacao_orcamento_itens", indexes = {
        @Index(name = "idx_orcamento_item_solicitacao", columnList = "solicitacao_id")})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SolicitacaoOrcamentoItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "solicitacao_id", nullable = false)
    private Long solicitacaoId;

    @Column(name = "prestador_id", nullable = false)
    private Long prestadorId;

    @Column(name = "prestador_nome", nullable = false)
    private String prestadorNome;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String mensagem;

    private Long atendimentoId;

    private LocalDateTime enviadoEm;
}
