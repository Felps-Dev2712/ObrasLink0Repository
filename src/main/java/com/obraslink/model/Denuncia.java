package com.obraslink.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Denúncia de um participante contra o outro num atendimento (cliente denuncia o prestador, ou vice-versa).
 * Cada um só pode denunciar o mesmo atendimento uma vez; fica pendente até um administrador analisar.
 */
@Entity
@Table(name = "denuncias")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Denuncia {
    public enum Motivo {
        COMPORTAMENTO_INADEQUADO("Comportamento inadequado"),
        NAO_COMPARECIMENTO("Não compareceu ou cancelou sem avisar"),
        COBRANCA_INDEVIDA("Cobrança indevida ou fora do combinado"),
        FRAUDE("Suspeita de fraude ou golpe"),
        OUTRO("Outro motivo");

        private final String rotulo;

        Motivo(String rotulo) {
            this.rotulo = rotulo;
        }

        public String getRotulo() {
            return rotulo;
        }
    }

    public enum Estado { PENDENTE, PROCEDENTE, IMPROCEDENTE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long atendimentoId;

    @Column(nullable = false)
    private Long autorUsuarioId;

    @Column(nullable = false)
    private Long denunciadoUsuarioId;

    @Column(nullable = false)
    private String autorNome;

    @Column(nullable = false)
    private String denunciadoNome;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Motivo motivo;

    @Column(nullable = false, length = 1000)
    private String descricao;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private Estado estado = Estado.PENDENTE;

    @Column(nullable = false)
    private LocalDateTime criadoEm;

    @Column(length = 1000)
    private String observacaoAnalise;

    private LocalDateTime analisadoEm;
}
