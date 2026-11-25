package com.obraslink.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pedido de orçamento que o cliente preenche uma única vez e que vira uma mensagem individual
 * (um {@link SolicitacaoOrcamentoItem}) para cada prestador selecionado. Todos os prestadores
 * selecionados precisam ter ao menos uma especialidade em comum; quando têm mais de uma em comum,
 * o cliente escolhe para quais delas preencher o formulário (ver {@code categoriasNomes}).
 */
@Entity
@Table(name = "solicitacoes_orcamento")
@Data
@NoArgsConstructor
public class SolicitacaoOrcamento {
    public enum Urgencia { BAIXA, MEDIA, ALTA }
    public enum Status { RASCUNHO, ENVIADA }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cliente_usuario_id", nullable = false)
    private Long clienteUsuarioId;

    /**
     * Especialidades escolhidas para esta solicitação (uma ou mais, entre as comuns a todos os
     * prestadores selecionados). Fica vazia enquanto o cliente ainda não escolheu, quando há mais
     * de uma especialidade em comum entre os prestadores selecionados.
     */
    @ElementCollection
    @CollectionTable(name = "solicitacao_orcamento_categorias", joinColumns = @JoinColumn(name = "solicitacao_id"))
    @OrderColumn(name = "posicao")
    @Column(name = "nome")
    private List<String> categoriasNomes = new ArrayList<>();

    // Endereço desta solicitação, independente do endereço cadastrado no perfil do cliente (ver OrcamentoService):
    // alterá-lo aqui nunca muda o cadastro, e cada solicitação pode ter o seu.
    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "endereco_execucao_id")
    private Endereco enderecoExecucao;

    @Column(columnDefinition = "TEXT")
    private String descricaoGeral;

    private LocalDate dataPrevisao;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Urgencia urgencia = Urgencia.MEDIA;

    @Column(columnDefinition = "TEXT")
    private String informacoesComplementares;

    /** Chave composta "{categoria}::{campo}", para não colidir quando duas categorias escolhidas usam o mesmo campo. */
    @ElementCollection
    @CollectionTable(name = "solicitacao_orcamento_campos", joinColumns = @JoinColumn(name = "solicitacao_id"))
    @MapKeyColumn(name = "campo")
    @Column(name = "valor", columnDefinition = "TEXT")
    private Map<String, String> camposEspecificos = new HashMap<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.RASCUNHO;

    @Column(nullable = false)
    private LocalDateTime criadoEm = LocalDateTime.now();

    /** Resumo das especialidades escolhidas, para exibição ("Pintor" ou "Pintor + Eletricista"). */
    @Transient
    public String getCategoriasResumo() {
        return categoriasNomes == null || categoriasNomes.isEmpty() ? "" : String.join(" + ", categoriasNomes);
    }

    @Builder
    public SolicitacaoOrcamento(Long id, Long clienteUsuarioId, List<String> categoriasNomes,
                                 Endereco enderecoExecucao, String descricaoGeral, LocalDate dataPrevisao,
                                 Urgencia urgencia, String informacoesComplementares, Map<String, String> camposEspecificos,
                                 Status status, LocalDateTime criadoEm) {
        this.id = id;
        this.clienteUsuarioId = clienteUsuarioId;
        this.categoriasNomes = categoriasNomes == null ? new ArrayList<>() : categoriasNomes;
        this.enderecoExecucao = enderecoExecucao;
        this.descricaoGeral = descricaoGeral;
        this.dataPrevisao = dataPrevisao;
        this.urgencia = urgencia == null ? Urgencia.MEDIA : urgencia;
        this.informacoesComplementares = informacoesComplementares;
        this.camposEspecificos = camposEspecificos == null ? new HashMap<>() : camposEspecificos;
        this.status = status == null ? Status.RASCUNHO : status;
        this.criadoEm = criadoEm == null ? LocalDateTime.now() : criadoEm;
    }
}
