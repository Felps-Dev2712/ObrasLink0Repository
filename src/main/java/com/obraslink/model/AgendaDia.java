package com.obraslink.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

/** Um dia específico da agenda do prestador: ele está livre ou já ocupado nessa data. */
@Entity
@Table(name = "agenda_dias", uniqueConstraints = @UniqueConstraint(columnNames = {"prestador_id", "data"}),
        indexes = @Index(name = "idx_agenda_dia_prestador", columnList = "prestador_id"))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgendaDia {
    /** SEM_INFORMACAO só existe para o formulário poder enviar "sem marcação" num &lt;select&gt;; nunca é gravado. */
    public enum Status { SEM_INFORMACAO, LIVRE, OCUPADO }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "prestador_id", nullable = false)
    private Long prestadorId;

    @Column(nullable = false)
    private LocalDate data;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status;

    public AgendaDia(Long prestadorId, LocalDate data, Status status) {
        this.prestadorId = prestadorId;
        this.data = data;
        this.status = status;
    }
}
