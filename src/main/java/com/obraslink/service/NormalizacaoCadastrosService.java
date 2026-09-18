package com.obraslink.service;

import com.obraslink.config.DocumentosDemonstracao;
import com.obraslink.validation.DadosCadastrais;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@RequiredArgsConstructor
public class NormalizacaoCadastrosService {
    private final JdbcTemplate jdbc;

    public record Pendencia(String tabela, Long id, String campo, String motivo) { }
    public record Relatorio(int camposAlterados, int documentosDemonstracaoCorrigidos, List<Pendencia> pendencias) { }
    private record Cadastro(Long id, String email, String documento, String telefone) { }

    @Transactional
    public Relatorio executar(boolean aplicar) {
        // Impede alterações concorrentes entre leitura, identificação de duplicados e gravação.
        if (aplicar) jdbc.execute("LOCK TABLE clientes, prestadores, telefones IN SHARE ROW EXCLUSIVE MODE");
        List<Pendencia> pendencias = new ArrayList<>();
        int[] contadores = new int[2];
        normalizarTabela("clientes", "telefone", aplicar, pendencias, contadores);
        normalizarTabela("prestadores", "whatsapp", aplicar, pendencias, contadores);
        List<Cadastro> telefones = jdbc.query("SELECT id, numero FROM telefones ORDER BY id",
                (rs, i) -> new Cadastro(rs.getLong("id"), null, null, rs.getString("numero")));
        for (Cadastro cadastro : telefones) {
            String novo = normalizarTelefone("telefones", cadastro, "numero", true, pendencias);
            if (!Objects.equals(novo, cadastro.telefone())) {
                if (aplicar) jdbc.update("UPDATE telefones SET numero = ? WHERE id = ?", novo, cadastro.id());
                contadores[0]++;
            }
        }
        return new Relatorio(contadores[0], contadores[1], List.copyOf(pendencias));
    }

    private void normalizarTabela(String tabela, String campoTelefone, boolean aplicar, List<Pendencia> pendencias, int[] contadores) {
        // tabela/campoTelefone são constantes internas; não recebem entrada HTTP.
        List<Cadastro> cadastros = jdbc.query("SELECT id, email, cpf_cnpj, " + campoTelefone + " FROM " + tabela + " ORDER BY id",
                (rs, i) -> new Cadastro(rs.getLong("id"), rs.getString("email"), rs.getString("cpf_cnpj"), rs.getString(campoTelefone)));
        Map<Long, String> candidatos = new HashMap<>();
        Map<String, Set<Long>> donos = new HashMap<>();
        for (Cadastro cadastro : cadastros) {
            String candidato = DocumentosDemonstracao.corrigirLegado(tabela, cadastro.email(), cadastro.documento());
            candidatos.put(cadastro.id(), candidato);
            // Considera tanto os valores atuais quanto os candidatos para não colidir com um registro preservado.
            for (String valor : Arrays.asList(cadastro.documento(), candidato)) {
                donos.computeIfAbsent(DadosCadastrais.documentoSemMascara(valor), key -> new HashSet<>()).add(cadastro.id());
            }
        }
        for (Cadastro cadastro : cadastros) {
            String candidato = candidatos.get(cadastro.id());
            String documento = cadastro.documento();
            if (!DadosCadastrais.documentoValido(candidato)) {
                pendencias.add(new Pendencia(tabela, cadastro.id(), "cpf_cnpj", "Documento inválido; confirmar o valor com o titular."));
            } else if (donos.get(DadosCadastrais.documentoSemMascara(candidato)).size() > 1) {
                pendencias.add(new Pendencia(tabela, cadastro.id(), "cpf_cnpj", "Documento duplicado; revisar os perfis antes de unificar o formato."));
            } else {
                documento = DadosCadastrais.documento(candidato);
                if (!DadosCadastrais.documentoSemMascara(documento).equals(DadosCadastrais.documentoSemMascara(cadastro.documento()))) contadores[1]++;
            }
            String telefone = normalizarTelefone(tabela, cadastro, campoTelefone, tabela.equals("prestadores"), pendencias);
            boolean documentoMudou = !Objects.equals(documento, cadastro.documento());
            boolean telefoneMudou = !Objects.equals(telefone, cadastro.telefone());
            if (documentoMudou || telefoneMudou) {
                if (aplicar) jdbc.update("UPDATE " + tabela + " SET cpf_cnpj = ?, " + campoTelefone + " = ? WHERE id = ?", documento, telefone, cadastro.id());
                contadores[0] += (documentoMudou ? 1 : 0) + (telefoneMudou ? 1 : 0);
            }
        }
    }

    private String normalizarTelefone(String tabela, Cadastro cadastro, String campo, boolean obrigatorio, List<Pendencia> pendencias) {
        try {
            return DadosCadastrais.telefone(cadastro.telefone(), obrigatorio);
        } catch (IllegalArgumentException ex) {
            pendencias.add(new Pendencia(tabela, cadastro.id(), campo, "Telefone inválido; confirmar DDD e número com o titular."));
            return cadastro.telefone();
        }
    }
}
