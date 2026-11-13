const {test} = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');

// Carrega o script num contexto mínimo (sem mapa na página) e pega a função de leitura do endereço reverso.
function carregar() {
    const window = {};
    const document = {readyState: 'complete', querySelectorAll: () => [], addEventListener() {}};
    vm.runInNewContext(fs.readFileSync('src/main/resources/static/js/localizacao.js', 'utf8'), {window, document});
    return window.ObraLinkEnderecoReverso;
}

test('"Usar minha localização" preenche CEP, rua, número, bairro, cidade e UF a partir do ponto', () => {
    const ler = carregar();
    const endereco = ler({address: {road: 'Rua Jerusalém', house_number: '330', suburb: 'Alto Boqueirão', city: 'Curitiba',
        'ISO3166-2-lvl4': 'BR-PR', postcode: '81770250', country_code: 'br'}});
    assert.deepEqual({...endereco}, {cep: '81770-250', rua: 'Rua Jerusalém', numero: '330', bairro: 'Alto Boqueirão', cidade: 'Curitiba', uf: 'PR'});
});

test('usa os campos alternativos do OpenStreetMap quando os principais faltam', () => {
    const ler = carregar();
    const endereco = ler({address: {pedestrian: 'Calçadão da XV', neighbourhood: 'Centro', town: 'Lapa', 'ISO3166-2-lvl4': 'BR-PR', country_code: 'br'}});
    assert.equal(endereco.rua, 'Calçadão da XV');
    assert.equal(endereco.bairro, 'Centro');
    assert.equal(endereco.cidade, 'Lapa');
    assert.equal(endereco.numero, '', 'sem número: o usuário informa');
    assert.equal(endereco.cep, '');
});

test('não devolve endereço (e nada é substituído) quando o ponto é fora do Brasil ou sem dados suficientes', () => {
    const ler = carregar();
    assert.equal(ler({address: {road: 'Av. Corrientes', city: 'Buenos Aires', country_code: 'ar'}}), null);
    assert.equal(ler({address: {country_code: 'br', state: 'Paraná'}}), null, 'sem cidade e sem rua/CEP');
    assert.equal(ler({error: 'Unable to geocode'}), null);
    assert.equal(ler(null), null);
});

// ---------- Arraste do marcador e clique no mapa: devem atualizar os campos de endereço ----------
// Mocka o Leaflet (L) e o seletor de endereço (data-address-picker) o suficiente para disparar
// dragend/click de verdade e conferir que os campos acima do mapa foram preenchidos.

function campoFalso(valorInicial = '') {
    const el = {value: valorInicial, _handlers: {}};
    el.addEventListener = (evento, cb) => { (el._handlers[evento] ||= []).push(cb); };
    el.focus = () => {};
    return el;
}

function montarPicker(valoresIniciais = {}) {
    const campos = {
        cep: campoFalso(valoresIniciais.cep), logradouro: campoFalso(valoresIniciais.logradouro),
        numero: campoFalso(valoresIniciais.numero), bairro: campoFalso(valoresIniciais.bairro),
        cidade: campoFalso(valoresIniciais.cidade), estado: campoFalso(valoresIniciais.estado),
        lat: campoFalso(valoresIniciais.lat), lng: campoFalso(valoresIniciais.lng),
    };
    const status = {textContent: '', dataset: {}};
    const caixa = {
        dataset: {},
        querySelector(seletor) {
            const m = seletor.match(/^\[data-geo="([a-z]+)"\]$/);
            if (m) return campos[m[1]] || null;
            if (seletor === '[data-geo-status]') return status;
            if (seletor === '[data-geo-map]') return {}; // o conteúdo não importa: L.map é mockado
            return null; // [data-geo-buscar] e [data-geo-gps]: não usados nestes testes
        },
        closest: () => null,
    };
    return {caixa, campos, status};
}

/** Leaflet mínimo: mapas e marcadores guardam seus próprios handlers e podem "disparar" eventos nos testes. */
function montarLeaflet() {
    const comEventos = alvo => {
        alvo._handlers = {};
        alvo.on = (evento, cb) => { (alvo._handlers[evento] ||= []).push(cb); return alvo; };
        alvo.fire = (evento, dado) => (alvo._handlers[evento] || []).forEach(cb => cb(dado));
        return alvo;
    };
    const mapas = [];
    const marcadores = [];
    const L = {
        map() { const m = comEventos({}); m.setView = () => m; m.fitBounds = () => m; mapas.push(m); return m; },
        tileLayer: () => ({addTo: () => {}}),
        divIcon: () => ({}),
        marker(ponto) {
            const m = comEventos({_latlng: Array.isArray(ponto) ? {lat: ponto[0], lng: ponto[1]} : ponto});
            m.addTo = () => m;
            m.getLatLng = () => ({...m._latlng});
            m.setLatLng = ll => { m._latlng = Array.isArray(ll) ? {lat: ll[0], lng: ll[1]} : {lat: ll.lat, lng: ll.lng}; return m; };
            m.bindPopup = () => m;
            marcadores.push(m);
            return m;
        },
        circle: () => ({addTo() { return this; }, setLatLng() { return this; }, setRadius() { return this; }, getBounds: () => ({})}),
    };
    return {L, mapas, marcadores};
}

/** Sobe o seletor de endereço com o Leaflet e o fetch mockados; devolve os campos e os objetos do mapa já criados. */
async function montarCenario(valoresIniciais, fetchMock) {
    const {caixa, campos, status} = montarPicker(valoresIniciais);
    const {L, mapas, marcadores} = montarLeaflet();
    const window = {L};
    const document = {readyState: 'complete', querySelectorAll: seletor => seletor === '[data-address-picker]' ? [caixa] : []};
    // "L" precisa existir também como global solto: no navegador window === globalThis, então window.L já é o L
    // bare que o resto do script usa; aqui window é só um objeto comum, por isso repassamos L à parte.
    vm.runInNewContext(fs.readFileSync('src/main/resources/static/js/localizacao.js', 'utf8'),
        {window, document, fetch: fetchMock, navigator: {}, console, L});
    await new Promise(resolve => setTimeout(resolve, 0)); // deixa o .then(carregarLeaflet) criar o mapa/marcador
    return {campos, status, mapa: mapas[0], marcador: marcadores[0]};
}

const ENDERECO_REVERSO = {address: {road: 'Rua Nova', house_number: '42', suburb: 'Centro', city: 'Curitiba',
    'ISO3166-2-lvl4': 'BR-PR', postcode: '80010000', country_code: 'br'}};

test('arrastar o marcador busca o endereço do novo ponto e preenche os campos acima', async () => {
    const fetchMock = async () => ({ok: true, json: async () => ENDERECO_REVERSO});
    const {campos, status, marcador} = await montarCenario(
        {lat: '-25.42', lng: '-49.27', logradouro: 'Rua Antiga', bairro: 'Bairro Antigo', cidade: 'Curitiba', estado: 'PR'}, fetchMock);

    marcador.setLatLng([-25.43, -49.28]); // simula o arraste chegando numa nova posição
    marcador.fire('dragend');
    await new Promise(resolve => setTimeout(resolve, 0));

    assert.equal(campos.lat.value, '-25.430000');
    assert.equal(campos.lng.value, '-49.280000');
    assert.equal(campos.cep.value, '80010-000');
    assert.equal(campos.logradouro.value, 'Rua Nova');
    assert.equal(campos.numero.value, '42');
    assert.equal(campos.bairro.value, 'Centro');
    assert.equal(status.textContent.includes('Ponto ajustado'), true);
    assert.equal(status.textContent.includes('Endereço atualizado'), true);
});

test('clicar no mapa também busca o endereço do ponto clicado e preenche os campos', async () => {
    const fetchMock = async () => ({ok: true, json: async () => ENDERECO_REVERSO});
    const {campos, status, mapa} = await montarCenario({}, fetchMock); // sem ponto ainda: primeiro clique define o endereço

    mapa.fire('click', {latlng: {lat: -25.43, lng: -49.28}});
    await new Promise(resolve => setTimeout(resolve, 0));

    assert.equal(campos.cidade.value, 'Curitiba');
    assert.equal(campos.estado.value, 'PR');
    assert.equal(campos.logradouro.value, 'Rua Nova');
    assert.equal(status.textContent.includes('Ponto marcado no mapa'), true);
});

test('sem endereço encontrado para o ponto, mantém os dados já preenchidos e avisa', async () => {
    const fetchMock = async () => ({ok: true, json: async () => ({})}); // resposta sem "address": endereço não encontrado
    const {campos, status, marcador} = await montarCenario(
        {lat: '-25.42', lng: '-49.27', logradouro: 'Rua Antiga', bairro: 'Bairro Antigo', cidade: 'Curitiba', estado: 'PR'}, fetchMock);

    marcador.setLatLng([-10, -60]); // um ponto qualquer, sem endereço encontrável na simulação
    marcador.fire('dragend');
    await new Promise(resolve => setTimeout(resolve, 0));

    assert.equal(campos.logradouro.value, 'Rua Antiga', 'endereço antigo preservado');
    assert.equal(campos.bairro.value, 'Bairro Antigo');
    assert.equal(campos.lat.value, '-10.000000', 'o ponto marcado é mantido mesmo sem achar o endereço');
    assert.equal(status.textContent.includes('Não encontramos o endereço'), true);
});

test('uma resposta antiga que chega depois não sobrescreve o resultado do arraste mais recente', async () => {
    const RESPOSTA_LENTA = {address: {road: 'Rua do Primeiro Arraste', suburb: 'Bairro Velho', city: 'Curitiba',
        'ISO3166-2-lvl4': 'BR-PR', country_code: 'br'}};
    const RESPOSTA_RAPIDA = {address: {road: 'Rua do Segundo Arraste', suburb: 'Bairro Novo', city: 'Curitiba',
        'ISO3166-2-lvl4': 'BR-PR', country_code: 'br'}};
    const fetchMock = async url => {
        const lenta = url.includes('lat=-11');
        return new Promise(resolve => setTimeout(
            () => resolve({ok: true, json: async () => (lenta ? RESPOSTA_LENTA : RESPOSTA_RAPIDA)}),
            lenta ? 40 : 5));
    };
    const {campos, marcador} = await montarCenario({lat: '-25.42', lng: '-49.27'}, fetchMock);

    marcador.setLatLng([-11, -46]); marcador.fire('dragend'); // demora mais para responder
    marcador.setLatLng([-22, -46]); marcador.fire('dragend'); // arrastado de novo antes da primeira resposta chegar
    await new Promise(resolve => setTimeout(resolve, 80)); // espera as duas respostas chegarem

    assert.equal(campos.logradouro.value, 'Rua do Segundo Arraste', 'vale o arraste mais recente, não o mais lento');
    assert.equal(campos.bairro.value, 'Bairro Novo');
});
