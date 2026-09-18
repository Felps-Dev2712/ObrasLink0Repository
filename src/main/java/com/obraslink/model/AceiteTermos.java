package com.obraslink.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "aceites_termos", uniqueConstraints = @UniqueConstraint(
        name = "uk_aceite_usuario_versao", columnNames = {"usuario_id", "versao"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AceiteTermos {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "usuario_id", nullable = false, updatable = false)
    private Long usuarioId;
    @Column(nullable = false, updatable = false, length = 40)
    private String versao;
    @Column(nullable = false, updatable = false)
    private Instant aceitoEm;

    public AceiteTermos(Long usuarioId, String versao) {
        this.usuarioId = usuarioId;
        this.versao = versao;
        this.aceitoEm = Instant.now();
    }
}
