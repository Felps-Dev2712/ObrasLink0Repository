package com.obraslink.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Pedido de orçamento que o cliente preenche uma única vez e que vira uma mensagem individual
 * (um {@link SolicitacaoOrcamentoItem}) para cada prestador selecionado, todos da mesma categoria.
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

    @Column(name = "categoria_id", nullable = false)
    private Long categoriaId;

    @Column(name = "categoria_nome", nullable = false)
    private String categoriaNome;

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

    @Builder
    public SolicitacaoOrcamento(Long id, Long clienteUsuarioId, Long categoriaId, String categoriaNome,
                                 Endereco enderecoExecucao, String descricaoGeral, LocalDate dataPrevisao,
                                 Urgencia urgencia, String informacoesComplementares, Map<String, String> camposEspecificos,
                                 Status status, LocalDateTime criadoEm) {
        this.id = id;
        this.clienteUsuarioId = clienteUsuarioId;
        this.categoriaId = categoriaId;
        this.categoriaNome = categoriaNome;
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
