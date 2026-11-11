package com.obraslink.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.obraslink.model.Endereco;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

/**
 * Endereço, coordenadas e distância.
 * - Valida e padroniza o endereço (sem chamada externa).
 * - Quando o navegador não enviou as coordenadas, busca no OpenStreetMap (Nominatim) com timeout.
 *   Deve ser chamado FORA de transação, antes de salvar.
 * - Calcula distância em linha reta (fórmula de Haversine).
 */
@Slf4j
@Service
public class LocalizacaoService {
    public static final int RAIO_PADRAO_KM = 15;
    public static final int RAIO_MINIMO_KM = 1;
    public static final int RAIO_MAXIMO_KM = 100;
    private static final double RAIO_TERRA_KM = 6371.0088;
    private static final ObjectMapper JSON = new ObjectMapper();

    private final HttpClient http;
    private final String urlBusca;
    private final boolean buscaHabilitada;

    public LocalizacaoService(@Value("${obralink.geocoding.url:https://nominatim.openstreetmap.org/search}") String urlBusca,
                              @Value("${obralink.geocoding.enabled:true}") boolean buscaHabilitada) {
        this.urlBusca = urlBusca;
        this.buscaHabilitada = buscaHabilitada;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();
    }

    // ---------- Distância ----------

    public static double distanciaKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * RAIO_TERRA_KM * Math.asin(Math.min(1, Math.sqrt(a)));
    }

    /** Distância entre dois endereços, arredondada a 0,1 km; vazio se algum não tiver coordenadas. */
    public static Optional<Double> distanciaKm(Endereco a, Endereco b) {
        if (a == null || b == null || !a.temCoordenadas() || !b.temCoordenadas()) return Optional.empty();
        double km = distanciaKm(a.getLatitude(), a.getLongitude(), b.getLatitude(), b.getLongitude());
        return Optional.of(Math.round(km * 10) / 10.0);
    }

    /** Coordenada arredondada (~1 km) para mostrar a região do prestador sem expor o endereço exato. */
    public static double aproximar(double coordenada) {
        return Math.round(coordenada * 100) / 100.0;
    }

    /** Limites aproximados do território brasileiro. */
    public static boolean coordenadasValidas(Double lat, Double lng) {
        return lat != null && lng != null && !lat.isNaN() && !lng.isNaN()
                && lat >= -34.0 && lat <= 6.0 && lng >= -74.5 && lng <= -28.5;
    }

    // ---------- Validação ----------

    /** Confere e padroniza os campos do endereço. Exige coordenadas válidas. Não faz chamada externa. */
    public static void validarEndereco(Endereco e) {
        if (e == null) throw new IllegalArgumentException("Informe o endereço.");
        String cep = e.getCep() == null ? "" : e.getCep().replaceAll("\\D", "");
        if (cep.length() != 8) throw new IllegalArgumentException("Informe um CEP com 8 dígitos.");
        e.setCep(cep.substring(0, 5) + "-" + cep.substring(5));
        e.setLogradouro(obrigatorio(e.getLogradouro(), "Informe a rua ou avenida."));
        e.setNumero(obrigatorio(e.getNumero(), "Informe o número do endereço."));
        e.setBairro(obrigatorio(e.getBairro(), "Informe o bairro."));
        e.setCidade(obrigatorio(e.getCidade(), "Informe a cidade."));
        String uf = obrigatorio(e.getEstado(), "Informe o estado (UF).").toUpperCase(Locale.ROOT);
        if (!uf.matches("[A-Z]{2}")) throw new IllegalArgumentException("Informe o estado com 2 letras (ex.: SP).");
        e.setEstado(uf);
        e.setComplemento(e.getComplemento() == null || e.getComplemento().isBlank() ? null : e.getComplemento().trim());
        if (!coordenadasValidas(e.getLatitude(), e.getLongitude())) {
            throw new IllegalArgumentException("Não encontramos esse endereço no mapa. Confira o CEP e o número ou marque o ponto no mapa.");
        }
    }

    public static int validarRaio(Integer raio) {
        if (raio == null) return RAIO_PADRAO_KM;
        if (raio < RAIO_MINIMO_KM || raio > RAIO_MAXIMO_KM) {
            throw new IllegalArgumentException("O raio de atendimento deve ficar entre " + RAIO_MINIMO_KM + " e " + RAIO_MAXIMO_KM + " km.");
        }
        return raio;
    }

    private static String obrigatorio(String valor, String mensagem) {
        if (valor == null || valor.isBlank()) throw new IllegalArgumentException(mensagem);
        return valor.trim();
    }

    // ---------- Busca de coordenadas ----------

    /**
     * Completa as coordenadas quando faltam ou são inválidas (ex.: navegador sem JavaScript).
     * Não lança erro: se a busca falhar, a validação do endereço avisa o usuário.
     */
    public void completarCoordenadas(Endereco e) {
        if (e == null || coordenadasValidas(e.getLatitude(), e.getLongitude())) return;
        e.setLatitude(null);
        e.setLongitude(null);
        localizar(e).ifPresent(c -> { e.setLatitude(c[0]); e.setLongitude(c[1]); });
    }

    /** Busca as coordenadas do endereço no Nominatim (OpenStreetMap). Timeout curto; vazio em caso de falha. */
    public Optional<double[]> localizar(Endereco e) {
        if (!buscaHabilitada || e == null || e.getCidade() == null || e.getCidade().isBlank()) return Optional.empty();
        String rua = ((e.getNumero() == null ? "" : e.getNumero().trim() + " ") + (e.getLogradouro() == null ? "" : e.getLogradouro())).trim();
        String url = urlBusca + "?format=json&limit=1&countrycodes=br"
                + param("street", rua) + param("city", e.getCidade()) + param("state", e.getEstado())
                + param("postalcode", e.getCep());
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .header("User-Agent", "ObraLink/0.1 (contato@obraslink.com.br)")
                    .header("Accept-Language", "pt-BR")
                    .GET().build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) return Optional.empty();
            return lerResposta(resp.body());
        } catch (Exception ex) {
            if (ex instanceof InterruptedException) Thread.currentThread().interrupt();
            log.warn("Busca de coordenadas indisponível: {}", ex.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    static Optional<double[]> lerResposta(String corpo) {
        try {
            JsonNode lista = JSON.readTree(corpo);
            if (!lista.isArray() || lista.isEmpty()) return Optional.empty();
            double lat = Double.parseDouble(lista.get(0).path("lat").asText());
            double lon = Double.parseDouble(lista.get(0).path("lon").asText());
            return coordenadasValidas(lat, lon) ? Optional.of(new double[]{lat, lon}) : Optional.empty();
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private static String param(String nome, String valor) {
        return valor == null || valor.isBlank() ? "" : "&" + nome + "=" + URLEncoder.encode(valor.trim(), StandardCharsets.UTF_8);
    }
}
