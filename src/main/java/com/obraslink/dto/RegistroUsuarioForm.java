package com.obraslink.dto;

import lombok.Data;

@Data
public class RegistroUsuarioForm {

    private String email;
    private String senha;
    private String confirmarSenha;

    private Boolean cadastroCliente = false;
    private Boolean cadastroPrestador = false;

    private String clienteNome;
    private String clienteCpfCnpj;
    private String clienteTelefone;

    private String prestadorNome;
    private String prestadorCpfCnpj;
    private String prestadorWhatsapp;
    private String prestadorDescricao;
    private Long categoriaId;
}
