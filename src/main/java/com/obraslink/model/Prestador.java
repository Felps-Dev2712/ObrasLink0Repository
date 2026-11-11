package com.obraslink.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "prestadores")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Prestador {
    @Transient
    public String getWhatsappFormatado() {
        return com.obraslink.validation.DadosCadastrais.telefoneValido(whatsapp)
                ? com.obraslink.validation.DadosCadastrais.telefone(whatsapp, true) : whatsapp;
    }

    @Transient
    public boolean isDadosCadastraisValidos() {
        return com.obraslink.validation.DadosCadastrais.documentoValido(cpfCnpj)
                && com.obraslink.validation.DadosCadastrais.telefoneValido(whatsapp);
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nome;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false, unique = true)
    private String cpfCnpj;

    private String whatsapp;

    @Column(columnDefinition = "TEXT")
    private String descricao;

    @Column(name = "usuario_id", unique = true)
    private Long usuarioId;

    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "endereco_id")
    private Endereco endereco;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categoria_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Categoria categoria;

    @ManyToMany
    @JoinTable(name="prestador_categorias", joinColumns=@JoinColumn(name="prestador_id"), inverseJoinColumns=@JoinColumn(name="categoria_id"), uniqueConstraints=@UniqueConstraint(columnNames={"prestador_id","categoria_id"}))
    @Builder.Default
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<Categoria> categorias = new ArrayList<>();

    @OneToMany(mappedBy = "prestador", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<Telefone> telefones = new ArrayList<>();

    @Column(precision = 10, scale = 2)
    private BigDecimal precoMinimo;

    private Integer anosExperiencia;

    @Column(nullable = false)
    @Builder.Default
    private Boolean ativo = true;

    // Chaves dos arquivos em <upload-dir>/imagens (nome aleatório, nunca caminho). Só o ImagemService altera.
    @Column(length = 64)
    private String fotoPerfilChave;

    @Column(length = 64)
    private String bannerChave;

    // Distância máxima (km), a partir do endereço do prestador, em que ele aceita atender.
    private Integer raioAtendimentoKm;

    @Transient
    public int getRaioEfetivoKm() {
        return raioAtendimentoKm == null ? com.obraslink.service.LocalizacaoService.RAIO_PADRAO_KM : raioAtendimentoKm;
    }

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime criadoEm;
    
    @OneToMany(mappedBy = "prestador", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<Servico> servicos = new ArrayList<>();
}
