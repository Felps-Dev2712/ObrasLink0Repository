package com.obraslink.dto;

import lombok.Data;

@Data
public class AvaliacaoPrestadorForm {

    private Long atendimentoId;
    private Integer qualidade;
    private Integer prazo;
    private Integer preco;
    private Integer organizacao;
    private Integer atendimento;
    private String comentario;
}
