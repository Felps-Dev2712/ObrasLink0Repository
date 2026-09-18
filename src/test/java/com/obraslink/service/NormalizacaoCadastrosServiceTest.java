package com.obraslink.service;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NormalizacaoCadastrosServiceTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final NormalizacaoCadastrosService service = new NormalizacaoCadastrosService(jdbc);

    private void carregar(String tabela, List<Map<String, Object>> rows) {
        doAnswer(invocation -> {
            RowMapper<?> mapper = invocation.getArgument(1);
            List<Object> mapped = new ArrayList<>();
            int index = 0;
            for (var row : rows) {
                ResultSet rs = mock(ResultSet.class);
                when(rs.getLong("id")).thenReturn(((Number) row.get("id")).longValue());
                when(rs.getString(anyString())).thenAnswer(call -> {
                    Object value = row.get(call.getArgument(0, String.class));
                    return value == null ? null : value.toString();
                });
                mapped.add(mapper.mapRow(rs, index++));
            }
            return mapped;
        }).when(jdbc).query(contains("FROM " + tabela + " ORDER"), any(RowMapper.class));
    }

    @Test
    void normalizaSemAlterarDigitosDeDadosReaisEPreservaInvalidos() {
        carregar("clientes", List.of(
                Map.of("id", 1L, "email", "real@exemplo.com", "cpf_cnpj", "52998224725", "telefone", "+55 11 99999-9999"),
                Map.of("id", 2L, "email", "outro@exemplo.com", "cpf_cnpj", "123", "telefone", "999")));
        var resultado = service.executar(true);
        assertEquals(2, resultado.camposAlterados());
        assertEquals(0, resultado.documentosDemonstracaoCorrigidos());
        assertEquals(2, resultado.pendencias().size());
        verify(jdbc).update("UPDATE clientes SET cpf_cnpj = ?, telefone = ? WHERE id = ?", "529.982.247-25", "(11) 99999-9999", 1L);
        verify(jdbc, never()).update(anyString(), any(), any(), eq(2L));
    }

    @Test
    void naoTentaUnificarDocumentosDuplicados() {
        carregar("clientes", List.of(
                Map.of("id", 1L, "cpf_cnpj", "52998224725"),
                Map.of("id", 2L, "cpf_cnpj", "529.982.247-25")));
        var resultado = service.executar(true);
        assertEquals(0, resultado.camposAlterados());
        assertEquals(2, resultado.pendencias().size());
        verify(jdbc, never()).update(anyString(), any(), any(), any());
    }

    @Test
    void simulacaoNaoGravaEReexecucaoComDadosNormalizadosNaoAltera() {
        carregar("prestadores", List.of(Map.of("id", 1L, "cpf_cnpj", "12ABC34501DE35", "whatsapp", "11999999999")));
        assertEquals(2, service.executar(false).camposAlterados());
        verify(jdbc, never()).execute(anyString());
        verify(jdbc, never()).update(anyString(), any(), any(), any());
        carregar("prestadores", List.of(Map.of("id", 1L, "cpf_cnpj", "12.ABC.345/01DE-35", "whatsapp", "(11) 99999-9999")));
        assertEquals(0, service.executar(true).camposAlterados());
    }

    @Test
    void atualizaTelefonesAdicionaisEDocumentosSeedReconhecidos() {
        carregar("clientes", List.of(Map.of("id", 1L, "email", "seed.cliente01@obraslink.com", "cpf_cnpj", "10000000001")));
        carregar("telefones", List.of(Map.of("id", 3L, "numero", "2123456789")));
        var resultado = service.executar(true);
        assertEquals(1, resultado.documentosDemonstracaoCorrigidos());
        assertEquals(2, resultado.camposAlterados());
        assertTrue(resultado.pendencias().isEmpty());
        verify(jdbc).update("UPDATE telefones SET numero = ? WHERE id = ?", "(21) 2345-6789", 3L);
    }
}
