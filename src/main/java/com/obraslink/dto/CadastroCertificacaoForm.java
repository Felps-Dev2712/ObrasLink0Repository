package com.obraslink.dto;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class CadastroCertificacaoForm {
    private String nome;
    private String emissor;
    private String dataEmissao;
    private String dataValidade;
    private String codigoRegistro;
    private MultipartFile documento;

    public boolean isVazio() {
        return (nome == null || nome.isBlank())
                && (emissor == null || emissor.isBlank())
                && (dataEmissao == null || dataEmissao.isBlank())
                && (dataValidade == null || dataValidade.isBlank())
                && (codigoRegistro == null || codigoRegistro.isBlank())
                && (documento == null || documento.isEmpty());
    }
}
