package com.obraslink.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Foto de um serviço do catálogo. O arquivo fica em <upload-dir>/imagens com a chave aleatória gravada aqui. */
@Entity
@Table(name = "servico_fotos", indexes = @Index(name = "idx_servico_foto_servico", columnList = "servico_id"))
@Getter
@NoArgsConstructor
public class ServicoFoto {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "servico_id", nullable = false, updatable = false)
    private Long servicoId;

    @Column(nullable = false, updatable = false, length = 64)
    private String chave;

    @Column(nullable = false, updatable = false)
    private LocalDateTime criadoEm = LocalDateTime.now();

    public ServicoFoto(Long servicoId, String chave) {
        this.servicoId = servicoId;
        this.chave = chave;
    }
}
