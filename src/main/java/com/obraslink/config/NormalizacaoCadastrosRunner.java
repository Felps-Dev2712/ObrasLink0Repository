package com.obraslink.config;

import com.obraslink.service.NormalizacaoCadastrosService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(100)
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "obraslink.cadastros.normalizar", havingValue = "true", matchIfMissing = true)
public class NormalizacaoCadastrosRunner implements CommandLineRunner {
    private final NormalizacaoCadastrosService service;

    @Override
    public void run(String... args) {
        var relatorio = service.executar(true);
        log.info("Padronização cadastral: {} campos atualizados, {} documentos de demonstração corrigidos, {} pendências.",
                relatorio.camposAlterados(), relatorio.documentosDemonstracaoCorrigidos(), relatorio.pendencias().size());
        relatorio.pendencias().forEach(p -> log.warn("Pendência cadastral: tabela={}, id={}, campo={}, motivo={}", p.tabela(), p.id(), p.campo(), p.motivo()));
    }
}
