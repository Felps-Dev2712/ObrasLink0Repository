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
