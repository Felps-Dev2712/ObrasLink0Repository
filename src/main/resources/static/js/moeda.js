// Máscara de valor em reais: <input data-mask="moeda">. Formata enquanto digita ("2000" → "R$ 2.000") e completa os
// centavos ao sair do campo ("R$ 2.000,00"). A vírgula separa os centavos; o ponto dos milhares é colocado sozinho.
// O servidor lê o mesmo formato (com.obraslink.validation.Moeda), então o valor salvo é exatamente o exibido.
(function (root) {
    'use strict';
    const MAX_DIGITOS_INTEIROS = 8; // coluna numeric(10,2): até 99.999.999,99

    /** Separa o texto em parte inteira e centavos ({inteiro, decimal, temVirgula}), descartando o que não for número. */
    function partes(texto) {
        let t = String(texto || '').replace(/R\$/g, '').replace(/\s/g, '');
        // "1500.75" (ponto como decimal, ex.: valor vindo do banco ou colado de planilha)
        if (!t.includes(',') && /^\d+\.\d{1,2}$/.test(t)) t = t.replace('.', ',');
        const virgula = t.indexOf(',');
        const temVirgula = virgula >= 0;
        const inteiro = (temVirgula ? t.slice(0, virgula) : t).replace(/\D/g, '').replace(/^0+(?=\d)/, '').slice(0, MAX_DIGITOS_INTEIROS);
        const decimal = temVirgula ? t.slice(virgula + 1).replace(/\D/g, '').slice(0, 2) : '';
        return {inteiro, decimal, temVirgula};
    }

    const milhares = inteiro => inteiro.replace(/\B(?=(\d{3})+(?!\d))/g, '.');

    /** Formatação durante a digitação: mantém o que a pessoa está escrevendo (ex.: "R$ 2.000," ou "R$ 2.000,5"). */
    function formatarDigitando(texto) {
        const {inteiro, decimal, temVirgula} = partes(texto);
        if (!inteiro && !temVirgula) return '';
        return 'R$ ' + milhares(inteiro || '0') + (temVirgula ? ',' + decimal : '');
    }

    /** Formatação final (ao sair do campo e ao carregar): sempre com 2 casas, "R$ 2.000,00". Vazio continua vazio. */
    function formatarFinal(texto) {
        const {inteiro, decimal, temVirgula} = partes(texto);
        if (!inteiro && !decimal) return '';
        return 'R$ ' + milhares(inteiro || '0') + ',' + (decimal + '00').slice(0, 2);
    }

    /** Valor numérico (para testes e conferências): "R$ 2.000,50" → 2000.5; vazio → null. */
    function paraNumero(texto) {
        const {inteiro, decimal} = partes(texto);
        if (!inteiro && !decimal) return null;
        return Number((inteiro || '0') + '.' + (decimal + '00').slice(0, 2));
    }

    function preparar(input) {
        if (input.dataset.moedaPronta) return; // o painel pode executar este script mais de uma vez
        input.dataset.moedaPronta = '1';
        input.addEventListener('input', () => {
            input.value = formatarDigitando(input.value);
            input.setSelectionRange(input.value.length, input.value.length); // cursor sempre no fim do valor
        });
        input.addEventListener('blur', () => { input.value = formatarFinal(input.value); });
        input.value = formatarFinal(input.value);
    }

    const api = {formatarDigitando, formatarFinal, paraNumero};
    if (typeof module !== 'undefined' && module.exports) module.exports = api;
    root.Moeda = api;
    if (typeof document !== 'undefined') {
        const iniciar = () => document.querySelectorAll('input[data-mask="moeda"]').forEach(preparar);
        if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', iniciar);
        else iniciar();
    }
})(typeof globalThis !== 'undefined' ? globalThis : this);
