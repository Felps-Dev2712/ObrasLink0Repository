package com.obraslink.service;

import com.obraslink.model.Endereco;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LocalizacaoServiceTest {

    private static Endereco endereco() {
        return Endereco.builder().cep("01310100").logradouro(" Avenida Paulista ").numero("1000")
                .bairro("Bela Vista").cidade("São Paulo").estado("sp").latitude(-23.5614).longitude(-46.6559).build();
    }

    @Test
    void calculaDistanciaConhecidaEntrePracaDaSeEAvenidaPaulista() {
        double km = LocalizacaoService.distanciaKm(-23.5505, -46.6333, -23.5614, -46.6559);
        assertEquals(2.6, km, 0.15);
        assertEquals(0.0, LocalizacaoService.distanciaKm(-23.5, -46.6, -23.5, -46.6), 1e-9);
    }

    @Test
    void distanciaEntreEnderecosArredondaEFicaVaziaSemCoordenadas() {
        Endereco a = endereco();
        Endereco b = Endereco.builder().latitude(-23.5505).longitude(-46.6333).build();
        double km = LocalizacaoService.distanciaKm(a, b).orElseThrow();
        assertEquals(Math.round(km * 10) / 10.0, km, 1e-9);
        b.setLatitude(null);
        assertTrue(LocalizacaoService.distanciaKm(a, b).isEmpty());
        assertTrue(LocalizacaoService.distanciaKm(null, a).isEmpty());
    }

    @Test
    void aproximaACoordenadaParaNaoExporOEnderecoExato() {
        assertEquals(-23.56, LocalizacaoService.aproximar(-23.561437), 1e-9);
        assertEquals(-46.66, LocalizacaoService.aproximar(-46.655881), 1e-9);
    }

    @Test
    void aceitaSoCoordenadasDentroDoBrasil() {
        assertTrue(LocalizacaoService.coordenadasValidas(-23.55, -46.63));
        assertFalse(LocalizacaoService.coordenadasValidas(40.71, -74.0));   // Nova York
        assertFalse(LocalizacaoService.coordenadasValidas(null, -46.63));
        assertFalse(LocalizacaoService.coordenadasValidas(Double.NaN, -46.63));
    }

    @Test
    void validarEnderecoPadronizaCepUfEEspacos() {
        Endereco e = endereco();
        e.setComplemento("   ");
        LocalizacaoService.validarEndereco(e);
        assertEquals("01310-100", e.getCep());
        assertEquals("SP", e.getEstado());
        assertEquals("Avenida Paulista", e.getLogradouro());
        assertNull(e.getComplemento());
    }

    @Test
    void validarEnderecoRecusaCamposInvalidos() {
        Endereco cep = endereco(); cep.setCep("123");
        assertTrue(assertThrows(IllegalArgumentException.class, () -> LocalizacaoService.validarEndereco(cep)).getMessage().contains("CEP"));
        Endereco numero = endereco(); numero.setNumero(" ");
        assertTrue(assertThrows(IllegalArgumentException.class, () -> LocalizacaoService.validarEndereco(numero)).getMessage().contains("número"));
        Endereco uf = endereco(); uf.setEstado("S1");
        assertThrows(IllegalArgumentException.class, () -> LocalizacaoService.validarEndereco(uf));
        assertThrows(IllegalArgumentException.class, () -> LocalizacaoService.validarEndereco(null));
    }

    @Test
    void validarEnderecoExigeCoordenadasValidas() {
        Endereco e = endereco(); e.setLatitude(null);
        assertTrue(assertThrows(IllegalArgumentException.class, () -> LocalizacaoService.validarEndereco(e)).getMessage().contains("mapa"));
        Endereco fora = endereco(); fora.setLatitude(48.85); fora.setLongitude(2.35);
        assertThrows(IllegalArgumentException.class, () -> LocalizacaoService.validarEndereco(fora));
    }

    @Test
    void raioUsaPadraoQuandoNuloERespeitaOsLimites() {
        assertEquals(LocalizacaoService.RAIO_PADRAO_KM, LocalizacaoService.validarRaio(null));
        assertEquals(1, LocalizacaoService.validarRaio(1));
        assertEquals(100, LocalizacaoService.validarRaio(100));
        assertThrows(IllegalArgumentException.class, () -> LocalizacaoService.validarRaio(0));
        assertThrows(IllegalArgumentException.class, () -> LocalizacaoService.validarRaio(101));
    }

    @Test
    void leARespostaDoNominatimEIgnoraRespostasRuins() {
        double[] c = LocalizacaoService.lerResposta("[{\"lat\":\"-23.5614\",\"lon\":\"-46.6559\"}]").orElseThrow();
        assertEquals(-23.5614, c[0], 1e-9);
        assertEquals(-46.6559, c[1], 1e-9);
        assertTrue(LocalizacaoService.lerResposta("[]").isEmpty());
        assertTrue(LocalizacaoService.lerResposta("não é json").isEmpty());
        assertTrue(LocalizacaoService.lerResposta("[{\"lat\":\"48.85\",\"lon\":\"2.35\"}]").isEmpty());
    }

    @Test
    void completarCoordenadasNaoBuscaQuandoJaTemOuQuandoDesligado() {
        LocalizacaoService desligado = new LocalizacaoService("http://127.0.0.1:9/nada", false);
        Endereco comPonto = endereco();
        desligado.completarCoordenadas(comPonto);
        assertEquals(-23.5614, comPonto.getLatitude(), 1e-9);

        Endereco invalido = endereco(); invalido.setLatitude(99.0);
        desligado.completarCoordenadas(invalido);
        assertNull(invalido.getLatitude());
        assertNull(invalido.getLongitude());
    }

    @Test
    void falhaDeRedeNaBuscaDevolveVazioSemLancarErro() {
        LocalizacaoService semServidor = new LocalizacaoService("http://127.0.0.1:9/search", true);
        assertTrue(semServidor.localizar(endereco()).isEmpty());
    }
}
