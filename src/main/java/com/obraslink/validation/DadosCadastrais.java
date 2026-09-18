package com.obraslink.validation;

import java.util.Locale;
import java.util.Set;

/** Validação estrutural: não consulta titularidade nem situação cadastral externa. */
public final class DadosCadastrais {
    private DadosCadastrais() { }
    private static final Set<String> DDDS = Set.of(
            "11", "12", "13", "14", "15", "16", "17", "18", "19", "21", "22", "24", "27", "28",
            "31", "32", "33", "34", "35", "37", "38", "41", "42", "43", "44", "45", "46", "47", "48", "49",
            "51", "53", "54", "55", "61", "62", "63", "64", "65", "66", "67", "68", "69",
            "71", "73", "74", "75", "77", "79", "81", "82", "83", "84", "85", "86", "87", "88", "89",
            "91", "92", "93", "94", "95", "96", "97", "98", "99");

    public static String documentoSemMascara(String valor) {
        return valor == null ? "" : valor.toUpperCase(Locale.ROOT).replaceAll("[.\\s/\\-]", "");
    }

    public static boolean documentoValido(String valor) {
        String numero = documentoSemMascara(valor);
        if (numero.matches("([0-9])\\1+")) return false;
        if (numero.matches("[0-9]{11}")) {
            String base = numero.substring(0, 9);
            base += digitoCpf(base);
            return numero.equals(base + digitoCpf(base));
        }
        if (numero.matches("[A-Z0-9]{12}[0-9]{2}")) {
            String base = numero.substring(0, 12);
            base += digitoCnpj(base);
            return numero.equals(base + digitoCnpj(base));
        }
        return false;
    }

    private static int digitoCpf(String base) {
        int soma = 0;
        for (int i = 0; i < base.length(); i++) soma += (base.charAt(i) - '0') * (base.length() + 1 - i);
        int resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }

    private static int digitoCnpj(String base) {
        int soma = 0, peso = 2;
        for (int i = base.length() - 1; i >= 0; i--) {
            soma += (base.charAt(i) - 48) * peso;
            peso = peso == 9 ? 2 : peso + 1;
        }
        int resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }

    public static String documento(String valor) {
        if (!documentoValido(valor)) throw new IllegalArgumentException("Informe um CPF ou CNPJ válido, incluindo os dígitos verificadores.");
        String n = documentoSemMascara(valor);
        return n.length() == 11
                ? n.substring(0, 3) + "." + n.substring(3, 6) + "." + n.substring(6, 9) + "-" + n.substring(9)
                : n.substring(0, 2) + "." + n.substring(2, 5) + "." + n.substring(5, 8) + "/" + n.substring(8, 12) + "-" + n.substring(12);
    }

    public static String telefoneSemMascara(String valor) {
        String n = valor == null ? "" : valor.replaceAll("[\\s().\\-]", "");
        if (n.startsWith("+55")) n = n.substring(3);
        else if ((n.length() == 12 || n.length() == 13) && n.startsWith("55")) n = n.substring(2);
        return n;
    }

    public static boolean telefoneValido(String valor) {
        String n = telefoneSemMascara(valor);
        return n.matches("(?:[0-9]{2}[2-5][0-9]{7}|[0-9]{2}9[0-9]{8})") && DDDS.contains(n.substring(0, 2));
    }

    public static String telefone(String valor, boolean obrigatorio) {
        if (valor == null || valor.isBlank()) {
            if (!obrigatorio) return null;
            throw new IllegalArgumentException("Informe um telefone com DDD.");
        }
        if (!telefoneValido(valor)) throw new IllegalArgumentException("Informe um telefone válido com DDD: fixo com 8 dígitos ou celular com 9 dígitos iniciado em 9.");
        String n = telefoneSemMascara(valor);
        int separador = n.length() - 4;
        return "(" + n.substring(0, 2) + ") " + n.substring(2, separador) + "-" + n.substring(separador);
    }
}
