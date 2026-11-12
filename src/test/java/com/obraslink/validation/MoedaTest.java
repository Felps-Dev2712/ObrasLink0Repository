package com.obraslink.validation;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class MoedaTest {

    @Test
    void leValoresNoFormatoBrasileiroEOsDigitadosSemFormatacao() {
        assertEquals(new BigDecimal("2000.00"), Moeda.ler("R$ 2.000,00"));
        assertEquals(new BigDecimal("2000.00"), Moeda.ler("2000"));
        assertEquals(new BigDecimal("2000.00"), Moeda.ler("2.000"));
        assertEquals(new BigDecimal("2000.50"), Moeda.ler("R$ 2.000,5"));
        assertEquals(new BigDecimal("58.66"), Moeda.ler("58,66"));
        assertEquals(new BigDecimal("1500.75"), Moeda.ler("1500.75"), "ponto como decimal (valor colado)");
        assertEquals(new BigDecimal("1234567.89"), Moeda.ler("R$ 1.234.567,89"));
        assertEquals(new BigDecimal("10.00"), Moeda.ler("R$ 10,"));
    }

    @Test
    void vazioViraNuloParaMostrarSobConsulta() {
        assertNull(Moeda.ler(null));
        assertNull(Moeda.ler(""));
        assertNull(Moeda.ler("  R$  "));
    }

    @Test
    void recusaTextoNegativoMaisDeDuasCasasEValorAcimaDaColuna() {
        for (String invalido : new String[]{"abc", "-10", "10,123", "1.23.4", "12,34,56", "2.00.000"}) {
            assertThrows(IllegalArgumentException.class, () -> Moeda.ler(invalido), invalido);
        }
        assertEquals(new BigDecimal("99999999.99"), Moeda.ler("R$ 99.999.999,99"));
        assertThrows(IllegalArgumentException.class, () -> Moeda.ler("R$ 100.000.000,00"));
    }

    @Test
    void formataComPontoNosMilharesEVirgulaNosCentavos() {
        assertEquals("R$ 2.000,00", Moeda.formatar(new BigDecimal("2000")));
        assertEquals("R$ 58,66", Moeda.formatar(new BigDecimal("58.66")));
        assertEquals("R$ 0,50", Moeda.formatar(new BigDecimal("0.5")));
        assertEquals("R$ 1.234.567,89", Moeda.formatar(new BigDecimal("1234567.89")));
        assertEquals("", Moeda.formatar(null));
    }

    @Test
    void editorDoFormularioFazOCaminhoDeIdaEVolta() {
        var editor = new Moeda.Editor();
        editor.setAsText("R$ 2.000,00");
        assertEquals(new BigDecimal("2000.00"), editor.getValue());
        assertEquals("R$ 2.000,00", editor.getAsText());
    }
}
