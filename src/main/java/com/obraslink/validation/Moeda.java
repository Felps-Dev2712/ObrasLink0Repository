package com.obraslink.validation;

import java.beans.PropertyEditorSupport;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Valores em reais no formato brasileiro: "R$ 2.000,00" (ponto nos milhares, vírgula nos centavos).
 * Mesmas regras do campo com máscara na tela (static/js/moeda.js), para o valor salvo ser exatamente o exibido.
 */
public final class Moeda {
    /** Limite da coluna preco (numeric(10,2)). */
    public static final BigDecimal MAXIMO = new BigDecimal("99999999.99");
    public static final String MENSAGEM_INVALIDO = "Informe um preço válido em reais, por exemplo R$ 2.000,00.";

    private Moeda() { }

    /** "R$ 2.000,00", "2000", "2.000,5", "1500.75" → BigDecimal com 2 casas; vazio → null. Lança erro se inválido. */
    public static BigDecimal ler(String texto) {
        if (texto == null) return null;
        String t = texto.replace("R$", "").replace(' ', ' ').trim().replace(" ", "");
        if (t.isEmpty()) return null;
        String normalizado;
        if (t.contains(",")) {
            if (!t.matches("\\d{1,3}(\\.\\d{3})*,\\d{0,2}|\\d+,\\d{0,2}")) throw new IllegalArgumentException(MENSAGEM_INVALIDO);
            normalizado = t.replace(".", "").replace(',', '.');
            if (normalizado.endsWith(".")) normalizado += "00";
        } else if (t.matches("\\d+\\.\\d{1,2}")) {
            normalizado = t; // ponto como decimal (ex.: valor colado de uma planilha)
        } else if (t.matches("\\d{1,3}(\\.\\d{3})+|\\d+")) {
            normalizado = t.replace(".", "");
        } else {
            throw new IllegalArgumentException(MENSAGEM_INVALIDO);
        }
        BigDecimal valor = new BigDecimal(normalizado).setScale(2, RoundingMode.UNNECESSARY);
        if (valor.compareTo(MAXIMO) > 0) throw new IllegalArgumentException("O preço pode ser de no máximo R$ 99.999.999,99.");
        return valor;
    }

    /** BigDecimal → "R$ 2.000,00"; null → "". */
    public static String formatar(BigDecimal valor) {
        if (valor == null) return "";
        DecimalFormat formato = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.forLanguageTag("pt-BR")));
        return "R$ " + formato.format(valor.setScale(2, RoundingMode.HALF_UP));
    }

    /** Para o binder do Spring: o formulário mostra e recebe o valor já no formato brasileiro. */
    public static class Editor extends PropertyEditorSupport {
        @Override public void setAsText(String texto) { setValue(ler(texto)); }
        @Override public String getAsText() { return formatar((BigDecimal) getValue()); }
    }
}
