const {test} = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');

test('alterar estrelas mantém notas independentes e não envia o formulário', () => {
    function group() {
        const status = {textContent: ''};
        const radios = Array.from({length: 5}, (_, i) => {
            const label = {selected: false, classList: {toggle: (_, active) => { label.selected = active; }}};
            return {value: String(i + 1), checked: false, label, closest: () => label,
                addEventListener(event, callback) { this[event] = callback; }};
        });
        return {radios, status, querySelectorAll: () => radios, querySelector: () => status};
    }
    const groups = Array.from({length: 5}, group);
    let submissions = 0;
    const context = {document: {querySelectorAll(selector) {
        if (selector === 'textarea[data-limite]' || selector === '[data-texto-recolhivel]') return [];
        assert.equal(selector, '[data-rating-criterion]');
        return groups;
    }}, HTMLFormElement: {submit: () => submissions++, requestSubmit: () => submissions++}};
    vm.runInNewContext(fs.readFileSync('src/main/resources/static/js/avaliacoes.js', 'utf8'), context);
    groups[0].radios[4].checked = true;
    groups[0].radios[4].change();
    groups[1].radios[1].checked = true;
    groups[1].radios[1].change();
    assert.equal(groups[0].status.textContent, '5 de 5 estrelas');
    assert.equal(groups[1].status.textContent, '2 de 5 estrelas');
    assert.equal(groups[2].status.textContent, 'Selecione uma nota');
    assert.equal(groups[1].radios.filter(r => r.label.selected).length, 2);
    assert.equal(submissions, 0);
});

test('comentário mostra contador, corta acima de 2.000 e não duplica no painel', () => {
    function elemento() {
        const classes = new Set();
        const atributos = {};
        return {classes, atributos, textContent: '', title: '', id: '',
            classList: {toggle: (c, on) => on ? classes.add(c) : classes.delete(c)},
            setAttribute(n, v) { atributos[n] = v; }, getAttribute: n => atributos[n] ?? null};
    }
    const contadores = [];
    const campo = Object.assign(elemento(), {id: 'comentario', value: '', dataset: {limite: '2000'}, maxLength: -1,
        addEventListener(evento, callback) { this['on' + evento] = callback; },
        insertAdjacentElement(_, el) { contadores.push(el); }});
    const context = {document: {
        querySelectorAll: selector => selector === 'textarea[data-limite]' ? [campo] : [],
        createElement: () => elemento()}};
    const codigo = fs.readFileSync('src/main/resources/static/js/avaliacoes.js', 'utf8');
    vm.runInNewContext(codigo, context);
    const contador = contadores[0];
    assert.equal(campo.maxLength, 2000);
    assert.equal(contador.textContent, '0/2.000');
    assert.equal(campo.getAttribute('aria-describedby'), 'comentario-contador');

    campo.value = 'a'.repeat(350); campo.oninput();
    assert.equal(contador.textContent, '350/2.000');
    assert.equal(contador.classes.size, 0);

    campo.value = 'a'.repeat(1850); campo.oninput();
    assert.ok(contador.classes.has('is-perto'));
    assert.equal(contador.getAttribute('aria-live'), 'polite');

    campo.value = 'a'.repeat(2600); campo.oninput(); // texto colado por fora do maxlength
    assert.equal(campo.value.length, 2000);
    assert.equal(contador.textContent, '2.000/2.000');
    assert.ok(contador.classes.has('is-limite'));

    vm.runInNewContext(codigo, context); // o painel executa o script de novo
    assert.equal(contadores.length, 1);
});

test('comentário longo começa recolhido com "Ver mais" e alterna para "Ver menos"', () => {
    function elemento(extra = {}) {
        const classes = new Set();
        const atributos = {};
        return Object.assign({classes, atributos, dataset: {}, hidden: false, textContent: '',
            classList: {toggle: (c, on) => on ? classes.add(c) : classes.delete(c), contains: c => classes.has(c)},
            setAttribute(n, v) { atributos[n] = v; }, getAttribute: n => atributos[n] ?? null,
            addEventListener(evento, callback) { this['on' + evento] = callback; }}, extra);
    }
    const botoes = [];
    const longo = elemento({id: 'comentario-avaliacao-1', scrollHeight: 400, clientHeight: 96,
        getBoundingClientRect: () => ({top: 100}), insertAdjacentElement(_, el) { botoes.push(el); }});
    const curto = elemento({id: 'comentario-avaliacao-2', scrollHeight: 48, clientHeight: 48,
        getBoundingClientRect: () => ({top: 100}), insertAdjacentElement(_, el) { botoes.push(el); }});
    const context = {window: {}, document: {
        querySelectorAll: s => s === '[data-texto-recolhivel]' ? [longo, curto] : [],
        createElement: () => elemento()}};
    const codigo = fs.readFileSync('src/main/resources/static/js/avaliacoes.js', 'utf8');
    vm.runInNewContext(codigo, context);

    const [botaoLongo, botaoCurto] = botoes;
    assert.equal(botaoLongo.hidden, false);
    assert.equal(botaoLongo.textContent, 'Ver mais');
    assert.equal(botaoLongo.getAttribute('aria-expanded'), 'false');
    assert.equal(botaoLongo.getAttribute('aria-controls'), 'comentario-avaliacao-1');
    assert.equal(botaoCurto.hidden, true, 'texto que cabe em 4 linhas não mostra o botão');

    botaoLongo.onclick();
    assert.ok(longo.classes.has('is-expanded'));
    assert.equal(botaoLongo.textContent, 'Ver menos');
    assert.equal(botaoLongo.getAttribute('aria-expanded'), 'true');

    botaoLongo.onclick();
    assert.ok(!longo.classes.has('is-expanded'));
    assert.equal(botaoLongo.textContent, 'Ver mais');

    vm.runInNewContext(codigo, context); // o painel executa o script de novo
    assert.equal(botoes.length, 2);
});

test('descrição de serviço longa mostra "Ver mais" como link para a página de detalhes', () => {
    const criados = [];
    const elemento = () => ({dataset: {}, hidden: false, textContent: '', tags: '',
        classList: {toggle() {}, contains: () => false}, setAttribute() {}, getAttribute: () => null,
        addEventListener(evento, callback) { this['on' + evento] = callback; }});
    const descricao = Object.assign(elemento(), {id: 'descricao-servico-5', scrollHeight: 300, clientHeight: 66,
        dataset: {verMaisHref: '/servicos/5'}, insertAdjacentElement(_, el) { criados.push(el); }});
    const curta = Object.assign(elemento(), {id: 'descricao-servico-6', scrollHeight: 40, clientHeight: 40,
        dataset: {verMaisHref: '/servicos/6'}, insertAdjacentElement(_, el) { criados.push(el); }});
    const context = {window: {}, document: {
        querySelectorAll: s => s === '[data-texto-recolhivel]' ? [descricao, curta] : [],
        createElement: tag => Object.assign(elemento(), {tag})}};
    vm.runInNewContext(fs.readFileSync('src/main/resources/static/js/avaliacoes.js', 'utf8'), context);

    const [link, linkCurto] = criados;
    assert.equal(link.tag, 'a');
    assert.equal(link.href, '/servicos/5');
    assert.equal(link.textContent, 'Ver mais');
    assert.equal(link.hidden, false);
    assert.equal(linkCurto.hidden, true, 'descrição que cabe em 3 linhas não mostra o link');
    assert.equal(link.onclick, undefined, 'o link navega; não expande dentro do card');
});
