(function (root) {
    'use strict';
    const ddds = new Set(('11 12 13 14 15 16 17 18 19 21 22 24 27 28 31 32 33 34 35 37 38 41 42 43 44 45 46 47 48 49 51 53 54 55 61 62 63 64 65 66 67 68 69 71 73 74 75 77 79 81 82 83 84 85 86 87 88 89 91 92 93 94 95 96 97 98 99').split(' '));
    const documentoLimpo = value => String(value || '').toUpperCase().replace(/[.\s/\-]/g, '');
    function telefoneLimpo(value) {
        let n = String(value || '').replace(/[\s().\-]/g, '');
        if (n.startsWith('+55')) n = n.slice(3);
        else if ((n.length === 12 || n.length === 13) && n.startsWith('55')) n = n.slice(2);
        return n;
    }
    function dv(base, cnpj) {
        let sum = 0, weight = 2;
        for (let i = base.length - 1; i >= 0; i--) {
            sum += (base.charCodeAt(i) - 48) * weight;
            weight = cnpj && weight === 9 ? 2 : weight + 1;
        }
        return sum % 11 < 2 ? 0 : 11 - sum % 11;
    }
    function documentoValido(value) {
        const n = documentoLimpo(value);
        if (/^([0-9])\1+$/.test(n)) return false;
        const cpf = /^[0-9]{11}$/.test(n);
        if (!cpf && !/^[A-Z0-9]{12}[0-9]{2}$/.test(n)) return false;
        let base = n.slice(0, cpf ? 9 : 12);
        base += dv(base, !cpf);
        return n === base + dv(base, !cpf);
    }
    function telefoneValido(value) {
        const n = telefoneLimpo(value);
        return /^(?:[0-9]{2}[2-5][0-9]{7}|[0-9]{2}9[0-9]{8})$/.test(n) && ddds.has(n.slice(0, 2));
    }
    function pattern(value, mask) {
        let result = '', pos = 0;
        for (const character of mask) {
            if (pos >= value.length) break;
            result += character === '#' ? value[pos++] : character;
        }
        return result;
    }
    function formatarDocumento(value) {
        const n = documentoLimpo(value);
        // Não trunca nem transforma conteúdo inválido em outro documento válido.
        if (!/^[A-Z0-9]*$/.test(n) || n.length > 14) return String(value || '').toUpperCase();
        return pattern(n, n.length > 11 || /[A-Z]/.test(n) ? '##.###.###/####-##' : '###.###.###-##');
    }
    function formatarTelefone(value, final = false) {
        const compact = String(value || '').replace(/[\s().\-]/g, '');
        const n = telefoneLimpo(value);
        if (!/^[0-9]*$/.test(n) || n.length > 11) return String(value || '');
        const prefix = !final && compact.startsWith('+55') ? '+55 ' : '';
        return prefix + pattern(n, n.length > 10 ? '(##) #####-####' : '(##) ####-####');
    }
    function preparar(input) {
        if (input.dataset.maskReady) return;
        input.dataset.maskReady = 'true';
        const documento = input.dataset.mask === 'documento';
        const format = documento ? formatarDocumento : formatarTelefone;
        const validate = documento ? documentoValido : telefoneValido;
        const message = documento ? 'Informe um CPF ou CNPJ válido, incluindo os dígitos verificadores.'
            : 'Informe um telefone válido com DDD: fixo com 8 dígitos ou celular com 9 dígitos iniciado em 9.';
        const error = document.createElement('small');
        error.id = input.id + '-validacao';
        error.className = 'field-validation';
        error.setAttribute('aria-live', 'polite');
        input.insertAdjacentElement('afterend', error);
        input.setAttribute('aria-describedby', [input.getAttribute('aria-describedby'), error.id].filter(Boolean).join(' '));
        function validar(show) {
            const invalid = !!input.value.trim() && !validate(input.value);
            input.setCustomValidity(invalid ? message : '');
            input.setAttribute('aria-invalid', String(invalid && show));
            error.textContent = show && invalid ? message : '';
        }
        function atualizar() {
            const before = input.value;
            const caret = input.selectionStart ?? before.length;
            const significant = before.slice(0, caret).replace(/[^A-Za-z0-9]/g, '').length;
            input.value = format(before);
            let pos = 0, count = 0;
            while (pos < input.value.length && count < significant) {
                if (/[A-Za-z0-9]/.test(input.value[pos])) count++;
                pos++;
            }
            if (caret === before.length) pos = input.value.length;
            input.setSelectionRange(pos, pos);
            validar(input.dataset.touched === 'true');
        }
        input.addEventListener('input', atualizar);
        input.addEventListener('beforeinput', event => {
            if (!['deleteContentBackward', 'deleteContentForward'].includes(event.inputType)
                || input.selectionStart !== input.selectionEnd) return;
            const backward = event.inputType === 'deleteContentBackward';
            const cursor = input.selectionStart;
            let position = backward ? cursor - 1 : cursor;
            if (position < 0 || position >= input.value.length || /[A-Za-z0-9]/.test(input.value[position])) return;
            while (position >= 0 && position < input.value.length && !/[A-Za-z0-9]/.test(input.value[position])) position += backward ? -1 : 1;
            if (position < 0 || position >= input.value.length) return;
            event.preventDefault();
            input.value = input.value.slice(0, position) + input.value.slice(position + 1);
            input.setSelectionRange(backward ? position : cursor, backward ? position : cursor);
            atualizar();
        });
        input.addEventListener('blur', () => {
            input.dataset.touched = 'true';
            if (validate(input.value)) input.value = format(input.value, true);
            validar(true);
        });
        input.addEventListener('invalid', () => validar(true));
        input.value = format(input.value);
        validar(!!input.value);
    }
    const api = {documentoValido, telefoneValido, formatarDocumento, formatarTelefone, documentoLimpo, telefoneLimpo};
    if (typeof module !== 'undefined' && module.exports) module.exports = api;
    root.DadosCadastrais = api;
    if (typeof document !== 'undefined') {
        const init = () => document.querySelectorAll('input[data-mask]').forEach(preparar);
        if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init);
        else init();
        document.addEventListener('htmx:afterSwap', init);
    }
})(typeof globalThis !== 'undefined' ? globalThis : this);
