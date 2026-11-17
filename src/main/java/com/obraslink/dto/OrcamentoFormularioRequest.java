package com.obraslink.dto;

import com.obraslink.model.Endereco;
import com.obraslink.model.SolicitacaoOrcamento;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/** Campos gerais + campos específicos (por chave da categoria) do formulário de solicitação de orçamento. */
@Data
public class OrcamentoFormularioRequest {
    /** true = usar o endereço cadastrado no perfil do cliente; false = usar o informado em enderecoExecucao. */
    private boolean usarEnderecoCadastrado = true;
    private Endereco enderecoExecucao = new Endereco();
    private String descricaoGeral;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dataPrevisao;
    private SolicitacaoOrcamento.Urgencia urgencia = SolicitacaoOrcamento.Urgencia.MEDIA;
    private String informacoesComplementares;
    private Map<String, String> campos = new HashMap<>();
}
