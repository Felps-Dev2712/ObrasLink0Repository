package com.obraslink.model;

import jakarta.persistence.*;
import lombok.*;

/** Um arquivo de referência (imagem) anexado pelo cliente a uma {@link SolicitacaoOrcamento}. */
@Entity
@Table(name = "solicitacao_orcamento_anexos")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SolicitacaoOrcamentoAnexo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "solicitacao_id", nullable = false)
    private Long solicitacaoId;

    @Column(nullable = false)
    private String chave;
}
