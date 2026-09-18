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
