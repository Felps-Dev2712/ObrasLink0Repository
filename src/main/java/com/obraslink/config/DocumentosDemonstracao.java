package com.obraslink.config;

import com.obraslink.validation.DadosCadastrais;
import java.util.regex.Pattern;

/** Dados sintéticos usados exclusivamente nas contas criadas pelo DataSeeder. */
public final class DocumentosDemonstracao {
    private DocumentosDemonstracao() { }

    public static String cpf(String prefixo, int index) {
        String base = prefixo + String.format("%06d", index);
        for (int tamanho = 9; tamanho <= 10; tamanho++) {
            int soma = 0;
            for (int i = 0; i < tamanho; i++) soma += (base.charAt(i) - '0') * (tamanho + 1 - i);
            int resto = soma % 11;
            base += resto < 2 ? 0 : 11 - resto;
        }
        return DadosCadastrais.documento(base);
    }

    /** CNPJ sintético com dígitos verificadores válidos: prefixo + índice + filial 0001. */
    public static String cnpj(String prefixo, int index) {
        String base = prefixo + String.format("%0" + (8 - prefixo.length()) + "d", index) + "0001";
        int[] pesos = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
        for (int tamanho = 12; tamanho <= 13; tamanho++) {
            int soma = 0;
            for (int i = 0; i < tamanho; i++) soma += (base.charAt(i) - '0') * pesos[i + 13 - tamanho];
            int resto = soma % 11;
            base += resto < 2 ? 0 : 11 - resto;
        }
        return DadosCadastrais.documento(base);
    }

    public static String corrigirLegado(String tabela, String email, String documento) {
        if (email == null) return documento;
        var match = Pattern.compile("seed\\.(cliente|prestador|ambos)([0-9]{2})@obraslink\\.com").matcher(email);
        if (!match.matches()) return documento;
        int index = Integer.parseInt(match.group(2));
        String perfil = match.group(1);
        int limite = perfil.equals("cliente") ? 12 : perfil.equals("prestador") ? 10 : 5;
        if (index < 1 || index > limite) return documento;
        String prefixo;
        if (tabela.equals("clientes") && !perfil.equals("prestador")) prefixo = perfil.equals("cliente") ? "100" : "200";
        else if (tabela.equals("prestadores") && !perfil.equals("cliente")) prefixo = perfil.equals("prestador") ? "300" : "400";
        else return documento;
        // Só corrige o valor exato produzido pelo gerador antigo. Dados editados pelo usuário são preservados.
        if (!DadosCadastrais.documentoSemMascara(documento).equals(prefixo + String.format("%08d", index))) return documento;
        return cpf(prefixo, index);
    }
}
