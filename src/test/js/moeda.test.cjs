const {test} = require('node:test');
const assert = require('node:assert/strict');
const {formatarDigitando, formatarFinal, paraNumero} = require('../../main/resources/static/js/moeda.js');

test('formata enquanto digita com ponto nos milhares', () => {
    assert.equal(formatarDigitando('2'), 'R$ 2');
    assert.equal(formatarDigitando('2000'), 'R$ 2.000');
    assert.equal(formatarDigitando('R$ 2.0001'), 'R$ 20.001');
    assert.equal(formatarDigitando('1234567'), 'R$ 1.234.567');
    assert.equal(formatarDigitando(''), '');
});

test('vírgula abre os centavos, com no máximo 2 casas', () => {
    assert.equal(formatarDigitando('R$ 2.000,'), 'R$ 2.000,');
    assert.equal(formatarDigitando('R$ 2.000,5'), 'R$ 2.000,5');
    assert.equal(formatarDigitando('R$ 2.000,555'), 'R$ 2.000,55');
    assert.equal(formatarDigitando(',5'), 'R$ 0,5');
});

test('ao sair do campo completa os centavos: 2000 vira R$ 2.000,00', () => {
    assert.equal(formatarFinal('2000'), 'R$ 2.000,00');
    assert.equal(formatarFinal('R$ 2.000,5'), 'R$ 2.000,50');
    assert.equal(formatarFinal('58,66'), 'R$ 58,66');
    assert.equal(formatarFinal('1500.75'), 'R$ 1.500,75', 'valor vindo com ponto decimal');
    assert.equal(formatarFinal('R$ 2.000,00'), 'R$ 2.000,00', 'valor já formatado vindo do servidor');
    assert.equal(formatarFinal(''), '');
});

test('ignora letras e sinais, zeros à esquerda e limita ao tamanho da coluna', () => {
    assert.equal(formatarFinal('abc-12x3'), 'R$ 123,00');
    assert.equal(formatarFinal('000150'), 'R$ 150,00');
    assert.equal(formatarDigitando('1234567890'), 'R$ 12.345.678');
});

test('preserva a precisão do valor informado', () => {
    assert.equal(paraNumero('R$ 2.000,50'), 2000.5);
    assert.equal(paraNumero('R$ 0,01'), 0.01);
    assert.equal(paraNumero(''), null);
});
