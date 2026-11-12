package com.obraslink.validation;

/**
 * Regra única para comentários das avaliações (cliente e prestador).
 * Conta os caracteres do mesmo jeito que a tela: o navegador envia cada quebra de linha como "\r\n"
 * (2 caracteres), mas o contador e o maxlength do textarea contam 1. Por isso a quebra é normalizada
 * para "\n" antes de medir e de gravar.
 */
public final class Comentario {
    public static final int LIMITE = 2000;
    public static final String MENSAGEM_LIMITE = "O comentário deve ter no máximo 2.000 caracteres.";

    private Comentario() { }

    /** Normaliza quebras de linha e espaços nas pontas; vazio vira null. Lança erro acima do limite. */
    public static String normalizar(String texto) {
        if (texto == null) return null;
        String normalizado = texto.replace("\r\n", "\n").replace('\r', '\n').strip();
        if (normalizado.isEmpty()) return null;
        if (normalizado.length() > LIMITE) throw new IllegalArgumentException(MENSAGEM_LIMITE);
        return normalizado;
    }
}
