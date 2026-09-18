package com.obraslink.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "clientes")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Cliente {
    @Transient
    public String getTelefoneFormatado() {
        return com.obraslink.validation.DadosCadastrais.telefoneValido(telefone)
                ? com.obraslink.validation.DadosCadastrais.telefone(telefone, false) : telefone;
    }

    @Transient
    public boolean isDadosCadastraisValidos() {
        return com.obraslink.validation.DadosCadastrais.documentoValido(cpfCnpj)
                && (telefone == null || telefone.isBlank() || com.obraslink.validation.DadosCadastrais.telefoneValido(telefone));
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

    private String telefone;

    @Column(name = "usuario_id", unique = true)
    private Long usuarioId;

    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "endereco_id")
    private Endereco endereco;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime criadoEm;
}
