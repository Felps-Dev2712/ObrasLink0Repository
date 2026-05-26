package com.obraslink.dto;

import lombok.Data;

@Data
public class AvaliacaoPrestadorForm {

    private Long servicoId;
    private Integer qualidade = 5;
    private Integer prazo = 5;
    private Integer preco = 5;
    private Integer organizacao = 5;
    private Integer atendimento = 5;
    private String comentario;
}
