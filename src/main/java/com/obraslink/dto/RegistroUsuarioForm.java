package com.obraslink.dto;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class RegistroUsuarioForm {

    private String email;
    private String nome;
    private String senha;
    private String confirmarSenha;

    private Boolean cadastroCliente = false;
    private Boolean cadastroPrestador = false;

    @Deprecated
    private String clienteNome;
    private String clienteCpfCnpj;
    private String clienteTelefone;
    private String clienteCep;
    private String clienteLogradouro;
    private String clienteNumero;
    private String clienteComplemento;
    private String clienteBairro;
    private String clienteCidade;
    private String clienteEstado;

    @Deprecated
    private String prestadorNome;
    private String prestadorCpfCnpj;
    private String prestadorWhatsapp;
    private String prestadorDescricao;
    private Long categoriaId;
    private List<Long> categoriasIds;
    private List<CadastroCertificacaoForm> certificacoes = new ArrayList<>();

    public void setCertificacoes(List<CadastroCertificacaoForm> certificacoes) {
        this.certificacoes = certificacoes == null ? new ArrayList<>() : new ArrayList<>(certificacoes);
    }
}
