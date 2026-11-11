package com.obraslink.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalTime;

@Entity @Table(name="disponibilidades", indexes=@Index(name="idx_disponibilidade_prestador", columnList="prestador_id"))
@Data @NoArgsConstructor
public class Disponibilidade {
    public enum DiaSemana { SEGUNDA, TERCA, QUARTA, QUINTA, SEXTA, SABADO, DOMINGO }
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="prestador_id", nullable=false) private Long prestadorId;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=12) private DiaSemana dia;
    @Column(nullable=false) private LocalTime inicio;
    @Column(nullable=false) private LocalTime fim;
    public Disponibilidade(Long prestadorId, DiaSemana dia, LocalTime inicio, LocalTime fim) { this.prestadorId=prestadorId; this.dia=dia; this.inicio=inicio; this.fim=fim; }
}
