package com.obraslink.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Permite trocar de conta sem digitar a senha de novo: a cada login, o navegador recebe um token (hash guardado
 * aqui) que identifica aquela conta neste navegador. O token em si fica só no cookie do cliente, nunca em texto
 * puro no banco — ver {@link com.obraslink.service.ContaVinculadaService}.
 */
@Entity
@Table(name = "contas_vinculadas", indexes = @Index(name = "idx_conta_vinculada_token", columnList = "tokenHash", unique = true))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContaVinculada {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long usuarioId;

    @Column(nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(nullable = false)
    private LocalDateTime criadoEm;

    @Column(nullable = false)
    private LocalDateTime expiraEm;

    private LocalDateTime ultimoAcessoEm;
}
