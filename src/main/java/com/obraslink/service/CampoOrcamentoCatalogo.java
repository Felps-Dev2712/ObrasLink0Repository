package com.obraslink.service;

import java.util.List;
import java.util.Map;

/**
 * Campos extras do formulário de orçamento, por categoria (além dos campos gerais, sempre presentes).
 * Categoria sem entrada aqui mostra só os campos gerais. A chave de cada campo é o que grava em
 * {@code SolicitacaoOrcamento.camposEspecificos} e o que entra na mensagem gerada por prestador.
 */
public final class CampoOrcamentoCatalogo {
    private CampoOrcamentoCatalogo() {}

    public record Campo(String chave, String rotulo, String tipo, boolean obrigatorio) {
        public Campo(String chave, String rotulo, String tipo) {
            this(chave, rotulo, tipo, true);
        }
    }

    private static final Map<String, List<Campo>> POR_CATEGORIA = Map.of(
            "Pintor", List.of(
                    new Campo("areaM2", "Área aproximada (m²)", "numero"),
                    new Campo("tipoSuperficie", "Tipo de superfície", "texto"),
                    new Campo("quantidadeComodos", "Quantidade de cômodos", "numero"),
                    new Campo("estadoSuperficie", "Estado atual da superfície", "texto"),
                    new Campo("necessitaPreparacao", "Precisa de preparação da superfície antes de pintar?", "texto"),
                    new Campo("materiaisDisponiveis", "Disponibilidade de materiais (tinta, lixa, etc.)", "texto", false)
            ),
            "Eletricista", List.of(
                    new Campo("tipoInstalacao", "Tipo de instalação", "texto"),
                    new Campo("tipoServico", "Tipo de serviço", "texto"),
                    new Campo("descricaoPontos", "Descrição dos pontos/equipamentos", "texto"),
                    new Campo("quantidadeAproximada", "Quantidade aproximada de pontos", "numero"),
                    new Campo("tensaoEletrica", "Tensão elétrica (se souber)", "texto", false),
                    new Campo("estadoInstalacao", "Estado atual da instalação", "texto")
            ),
            "Mestre de Obras", List.of(
                    new Campo("tipoObra", "Tipo de obra", "texto"),
                    new Campo("etapaAtual", "Etapa atual da obra", "texto"),
                    new Campo("areaM2", "Área aproximada (m²)", "numero"),
                    new Campo("servicosExecutar", "Serviços a executar", "texto"),
                    new Campo("existeProjeto", "Já existe projeto?", "texto"),
                    new Campo("materiaisDisponiveis", "Disponibilidade de materiais", "texto", false),
                    new Campo("previsaoInicioDuracao", "Previsão de início e duração", "texto", false)
            )
    );

    /** Campos específicos da categoria; vazio quando ela só usa os campos gerais. */
    public static List<Campo> campos(String categoriaNome) {
        return POR_CATEGORIA.getOrDefault(categoriaNome, List.of());
    }
}
