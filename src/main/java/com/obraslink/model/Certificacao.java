package com.obraslink.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity @Table(name="certificacoes", indexes=@Index(name="idx_certificacao_prestador", columnList="prestador_id"))
@Data @NoArgsConstructor
public class Certificacao {
    public enum Estado { PENDENTE, APROVADA, REPROVADA }
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="prestador_id", nullable=false) private Long prestadorId;
    @Column(nullable=false, length=180) private String nome;
    @Column(nullable=false, length=180) private String emissor;
    @Column(nullable=false) private LocalDate dataEmissao;
    private LocalDate dataValidade;
    @Column(length=100) private String codigoRegistro;
    @Column(nullable=false, length=255) private String documentoChave;
    @Column(nullable=false, length=180) private String documentoNome;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=12) private Estado estado=Estado.PENDENTE;
    @Column(length=1000) private String observacaoValidacao;
    @Column(nullable=false, updatable=false) private LocalDateTime criadoEm=LocalDateTime.now();
}
