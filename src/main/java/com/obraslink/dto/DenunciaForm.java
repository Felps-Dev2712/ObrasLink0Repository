package com.obraslink.dto;

import com.obraslink.model.Denuncia;
import lombok.Data;

@Data
public class DenunciaForm {
    private Denuncia.Motivo motivo;
    private String descricao;
}
