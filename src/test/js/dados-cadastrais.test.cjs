const {test} = require('node:test');
const assert = require('node:assert/strict');
const dados = require('../../main/resources/static/js/dados-cadastrais.js');

test('CPF, CNPJ numérico e alfanumérico validam dígitos verificadores', () => {
    for (const valor of ['529.982.247-25', '52998224725', '04.252.011/0001-10', '12.ABC.345/01DE-35', '12abc34501de35', '00.000.000/E08G-12']) assert.equal(dados.documentoValido(valor), true, valor);
    for (const valor of ['', '11111111111', '00000000000000', '52998224724', '04252011000111', '12ABC34501DE36', '52998224725x', '529982247250', '12ABC34501DE3A', '52998224725!']) assert.equal(dados.documentoValido(valor), false, valor);
});
test('formata cada etapa e troca CPF por CNPJ conforme o comprimento', () => {
    assert.equal(dados.formatarDocumento('5299'), '529.9');
    assert.equal(dados.formatarDocumento('52998224725'), '529.982.247-25');
    assert.equal(dados.formatarDocumento('042520110001'), '04.252.011/0001');
    assert.equal(dados.formatarDocumento('12abc34501de35'), '12.ABC.345/01DE-35');
    assert.equal(dados.formatarDocumento('529982247251234'), '529982247251234');
    assert.equal(dados.formatarDocumento('52998224725!'), '52998224725!');
});
test('aceita fixo, celular e DDI 55 sem confundir com DDD 55', () => {
    for (const valor of ['(11) 99999-9999', '+55 (11) 99999-9999', '5511999999999', '(55) 99999-9999', '(21) 2345-6789']) assert.equal(dados.telefoneValido(valor), true, valor);
    for (const valor of ['123', '999999999', '(20) 99999-9999', '(11) 89999-9999', '(11) 9999-9999', '+1 2345678901', '11999999999 ramal 2', '11999999999x']) assert.equal(dados.telefoneValido(valor), false, valor);
    assert.equal(dados.formatarTelefone('11999999999'), '(11) 99999-9999');
    assert.equal(dados.formatarTelefone('2123456789'), '(21) 2345-6789');
    assert.equal(dados.formatarTelefone('55999999999'), '(55) 99999-9999');
    assert.equal(dados.formatarTelefone('+55 (11) 99999-9999', true), '(11) 99999-9999');
});
test('digitação progressiva e exclusão não perdem os números', () => {
    let value = '';
    for (const digit of '52998224725') value = dados.formatarDocumento(value + digit);
    assert.equal(value, '529.982.247-25');
    value = dados.formatarDocumento(value.slice(0, -1));
    assert.equal(value, '529.982.247-2');
    value = '';
    for (const digit of '11999999999') value = dados.formatarTelefone(value + digit);
    assert.equal(value, '(11) 99999-9999');
});
