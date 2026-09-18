package com.obraslink.dto;

import lombok.Data;

@Data
public class AvaliacaoClienteForm {
    private Integer comunicacao;
    private Integer pontualidade;
    private Integer cumprimentoCombinado;
    private String comentario;
}
