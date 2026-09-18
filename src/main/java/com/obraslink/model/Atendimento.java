package com.obraslink.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "atendimentos", indexes = {
        @Index(name = "idx_atendimento_cliente", columnList = "cliente_usuario_id"),
        @Index(name = "idx_atendimento_fornecedor", columnList = "prestador_usuario_id")})
@Data
@NoArgsConstructor
public class Atendimento {
    public enum Status { CONTATO_SOLICITADO, EM_ANDAMENTO, CONCLUIDO }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "cliente_usuario_id", nullable = false) private Long clienteUsuarioId;
    @Column(name = "prestador_usuario_id", nullable = false) private Long prestadorUsuarioId;
    @Column(nullable = false) private Long prestadorId;
    @Column(nullable = false) private String clienteNome;
    @Column(nullable = false) private String prestadorNome;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private Status status = Status.CONTATO_SOLICITADO;
    @Column(nullable = false) private LocalDateTime criadoEm = LocalDateTime.now();
    private LocalDateTime confirmadoEm;
    private LocalDateTime concluidoEm;
}
