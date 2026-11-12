package com.obraslink.validation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ComentarioTest {

    @Test
    void aceitaExatamenteOLimite() {
        assertEquals(2000, Comentario.normalizar("a".repeat(2000)).length());
    }

    @Test
    void recusaUmCaractereAlemDoLimiteComMensagemClara() {
        var erro = assertThrows(IllegalArgumentException.class, () -> Comentario.normalizar("a".repeat(2001)));
        assertEquals("O comentário deve ter no máximo 2.000 caracteres.", erro.getMessage());
    }

    @Test
    void quebraDeLinhaEnviadaPeloNavegadorContaComoUmCaractere() {
        // 1.999 letras + 1 quebra = 2.000 no contador da tela; o navegador envia a quebra como "\r\n" (2.001 no texto bruto).
        String enviado = "a".repeat(1000) + "\r\n" + "b".repeat(999);
        String salvo = Comentario.normalizar(enviado);
        assertEquals(2000, salvo.length());
        assertFalse(salvo.contains("\r"));
    }

    @Test
    void espacosNasPontasNaoContamEVazioViraNulo() {
        assertEquals("ótimo", Comentario.normalizar("   ótimo \n "));
        assertEquals(2000, Comentario.normalizar("  " + "a".repeat(2000) + "  ").length());
        assertNull(Comentario.normalizar("   \r\n  "));
        assertNull(Comentario.normalizar(null));
    }
}
