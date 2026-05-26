package com.obraslink.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "avaliacoes_prestadores")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AvaliacaoPrestador {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prestador_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Prestador prestador;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Cliente cliente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "servico_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Servico servico;

    @Column(nullable = false)
    private Integer qualidade;

    @Column(nullable = false)
    private Integer prazo;

    @Column(nullable = false)
    private Integer preco;

    @Column(nullable = false)
    private Integer organizacao;

    @Column(nullable = false)
    private Integer atendimento;

    @Column(columnDefinition = "TEXT")
    private String comentario;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime criadoEm;
}
