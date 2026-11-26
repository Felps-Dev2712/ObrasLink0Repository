package com.obraslink.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Plano de assinatura do prestador: define limites de uso do catálogo e se ele tem
 * selo de destaque/prioridade na busca. {@code Integer.MAX_VALUE} representa "ilimitado".
 * Os prestadores sempre começam no {@link #GRATUITO} (ver {@code Prestador.plano}).
 */
public enum Plano {
    GRATUITO("Gratuito", "Para começar a receber contatos na plataforma.",
            BigDecimal.ZERO, 3, 1, 15, 2, false),
    ESSENCIAL("Essencial", "Mais espaço no catálogo e mais alcance de atendimento.",
            new BigDecimal("29.90"), 8, 3, 30, 3, false),
    PROFISSIONAL("Profissional", "Selo de destaque e prioridade nas buscas da vitrine.",
            new BigDecimal("59.90"), 20, 10, 60, 6, true),
    EMPRESARIAL("Empresarial", "Sem limites de catálogo, para equipes com alta demanda.",
            new BigDecimal("129.90"), Integer.MAX_VALUE, Integer.MAX_VALUE, 100, Integer.MAX_VALUE, true);

    private final String nome;
    private final String descricaoCurta;
    private final BigDecimal precoMensal;
    private final int maxServicosAtivos;
    private final int maxCertificacoesAtivas;
    private final int raioMaximoKm;
    private final int maxFotosPorServico;
    private final boolean destaque;

    Plano(String nome, String descricaoCurta, BigDecimal precoMensal, int maxServicosAtivos,
          int maxCertificacoesAtivas, int raioMaximoKm, int maxFotosPorServico, boolean destaque) {
        this.nome = nome;
        this.descricaoCurta = descricaoCurta;
        this.precoMensal = precoMensal;
        this.maxServicosAtivos = maxServicosAtivos;
        this.maxCertificacoesAtivas = maxCertificacoesAtivas;
        this.raioMaximoKm = raioMaximoKm;
        this.maxFotosPorServico = maxFotosPorServico;
        this.destaque = destaque;
    }

    public String getNome() { return nome; }
    public String getDescricaoCurta() { return descricaoCurta; }
    public BigDecimal getPrecoMensal() { return precoMensal; }
    public int getMaxServicosAtivos() { return maxServicosAtivos; }
    public int getMaxCertificacoesAtivas() { return maxCertificacoesAtivas; }
    public int getRaioMaximoKm() { return raioMaximoKm; }
    public int getMaxFotosPorServico() { return maxFotosPorServico; }
    public boolean isDestaque() { return destaque; }
    public boolean isGratuito() { return this == GRATUITO; }

    private static String servicosFrase(int max) {
        return max == Integer.MAX_VALUE ? "Serviços ativos ilimitados no catálogo" : "Até " + max + " serviço" + (max == 1 ? "" : "s") + " ativo" + (max == 1 ? "" : "s") + " no catálogo";
    }

    private static String certificacoesFrase(int max) {
        return max == Integer.MAX_VALUE ? "Certificações simultâneas ilimitadas"
                : "Até " + max + " certificaç" + (max == 1 ? "ão" : "ões") + " simultânea" + (max == 1 ? "" : "s");
    }

    private static String fotosFrase(int max) {
        return max == Integer.MAX_VALUE ? "Fotos ilimitadas por serviço" : "Até " + max + " foto" + (max == 1 ? "" : "s") + " por serviço";
    }

    /** Lista de recursos para exibir na tela de planos, montada a partir dos próprios limites (nunca desalinhada deles). */
    public List<String> getRecursos() {
        List<String> r = new ArrayList<>();
        r.add(servicosFrase(maxServicosAtivos));
        r.add(certificacoesFrase(maxCertificacoesAtivas));
        r.add("Até " + raioMaximoKm + " km de raio de atendimento");
        r.add(fotosFrase(maxFotosPorServico));
        if (destaque) {
            r.add("Selo de destaque no perfil e na vitrine");
            r.add("Prioridade na ordenação das buscas");
        }
        return r;
    }
}
