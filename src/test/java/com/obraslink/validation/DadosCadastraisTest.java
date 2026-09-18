package com.obraslink.validation;

import com.obraslink.config.DocumentosDemonstracao;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DadosCadastraisTest {
    @ParameterizedTest
    @ValueSource(strings = {"529.982.247-25", "52998224725", "04.252.011/0001-10", "04252011000110", "12.ABC.345/01DE-35", "12abc34501de35", "00.000.000/E08G-12"})
    void aceitaDocumentosComVerificadoresCorretos(String valor) {
        assertTrue(DadosCadastrais.documentoValido(valor));
        assertTrue(DadosCadastrais.documentoValido(DadosCadastrais.documento(valor)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "11111111111", "00000000000000", "52998224724", "04252011000111", "12ABC34501DE36", "52998224725x", "529982247250", "12ABC34501DE3A", "52998224725!"})
    void rejeitaDocumentosIncompletosAdulteradosOuRepetidos(String valor) {
        assertFalse(DadosCadastrais.documentoValido(valor));
        assertThrows(IllegalArgumentException.class, () -> DadosCadastrais.documento(valor));
    }

    @ParameterizedTest
    @ValueSource(strings = {"(11) 99999-9999", "11999999999", "+55 (11) 99999-9999", "5511999999999", "(55) 99999-9999", "(21) 2345-6789", "+55 (21) 2345-6789"})
    void aceitaFixoCelularEDdiBrasil(String valor) {
        assertTrue(DadosCadastrais.telefoneValido(valor));
        assertTrue(DadosCadastrais.telefoneValido(DadosCadastrais.telefone(valor, true)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"123", "999999999", "(20) 99999-9999", "(11) 89999-9999", "(11) 9999-9999", "+1 2345678901", "11999999999 ramal 2", "11999999999x", "00000000000"})
    void rejeitaDddInexistenteNumerosIncompletosELixo(String valor) {
        assertFalse(DadosCadastrais.telefoneValido(valor));
        assertThrows(IllegalArgumentException.class, () -> DadosCadastrais.telefone(valor, true));
    }

    @Test
    void mascaraSemTrocarDigitosENaoConfundeDdd55ComDdi() {
        assertEquals("529.982.247-25", DadosCadastrais.documento("52998224725"));
        assertEquals("12.ABC.345/01DE-35", DadosCadastrais.documento("12abc34501de35"));
        assertEquals("(55) 99999-9999", DadosCadastrais.telefone("55999999999", true));
        assertEquals("(11) 99999-9999", DadosCadastrais.telefone("+55 11 99999-9999", true));
        assertNull(DadosCadastrais.telefone(" ", false));
        assertThrows(IllegalArgumentException.class, () -> DadosCadastrais.telefone(null, true));
    }

    @Test
    void corrigeSomenteValoresExatosDoGeradorDeDemonstracao() {
        String novo = DocumentosDemonstracao.corrigirLegado("clientes", "seed.cliente01@obraslink.com", "10000000001");
        assertTrue(DadosCadastrais.documentoValido(novo));
        assertEquals(novo, DocumentosDemonstracao.corrigirLegado("clientes", "seed.cliente01@obraslink.com", novo));
        assertEquals("10000000001", DocumentosDemonstracao.corrigirLegado("clientes", "real@exemplo.com", "10000000001"));
        assertEquals("123", DocumentosDemonstracao.corrigirLegado("clientes", "seed.cliente01@obraslink.com", "123"));
        for (String prefixo : new String[]{"100", "200", "300", "400"}) {
            for (int i = 1; i <= 12; i++) assertTrue(DadosCadastrais.documentoValido(DocumentosDemonstracao.cpf(prefixo, i)));
        }
    }
}
