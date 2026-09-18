package com.obraslink.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "avaliacoes_clientes")
public class AvaliacaoCliente {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true)
    private Long atendimentoId;
    @Column(nullable = false)
    private Long clienteUsuarioId;
    @Column(nullable = false)
    private Long prestadorUsuarioId;
    @Column(nullable = false)
    private Integer comunicacao;
    @Column(nullable = false)
    private Integer pontualidade;
    @Column(nullable = false)
    private Integer cumprimentoCombinado;
    @Column(length = 2000)
    private String comentario;
    @Column(nullable = false)
    private LocalDateTime criadoEm = LocalDateTime.now();
}
