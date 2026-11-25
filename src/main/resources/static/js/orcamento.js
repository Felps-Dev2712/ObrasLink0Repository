// Seleção de um ou vários prestadores, na vitrine, para pedir orçamento em conjunto. Cada prestador pode
// ter mais de uma especialidade: ao marcar o primeiro, trava nas especialidades DELE (uma ou várias); ao
// marcar outros, vai reduzindo para só as especialidades em comum entre todos os marcados até agora — os
// checkboxes de prestadores sem nenhuma especialidade em comum ficam desabilitados. O mesmo cálculo cobre
// tanto selecionar um só prestador com várias especialidades quanto vários prestadores de uma vez.
// A barra flutuante só aparece com pelo menos um marcado. Delegação no document (como o dashboard.js):
// funciona mesmo quando o painel.js troca o conteúdo sem recarregar a página, já que a #orcamento-form só
// existe na tela de prestadores.
(() => {
    function caixas() {
        return [...document.querySelectorAll('input[type=checkbox][name=prestadorId]')];
    }

    function especialidadesDe(caixa) {
        const ids = (caixa.dataset.especialidadesIds || '').split(',').filter(Boolean).map(Number);
        const nomes = (caixa.dataset.especialidadesNomes || '').split(',').filter(Boolean);
        return ids.map((id, i) => ({ id, nome: nomes[i] }));
    }

    function atualizar() {
        const barra = document.getElementById('orcamento-form');
        if (!barra) return;
        const todas = caixas();
        const marcadas = todas.filter(c => c.checked);

        // Interseção das especialidades de todos os prestadores já marcados.
        let comuns = null;
        const nomePorId = new Map();
        marcadas.forEach(c => {
            const especialidades = especialidadesDe(c);
            especialidades.forEach(e => nomePorId.set(e.id, e.nome));
            const ids = new Set(especialidades.map(e => e.id));
            comuns = comuns === null ? ids : new Set([...comuns].filter(id => ids.has(id)));
        });

        todas.forEach(c => {
            const ids = especialidadesDe(c).map(e => e.id);
            const compativel = !comuns || c.checked || ids.some(id => comuns.has(id));
            c.disabled = !compativel;
            c.closest('.pro-card-select')?.classList.toggle('is-disabled', c.disabled);
        });

        barra.hidden = marcadas.length === 0;
        const contagem = barra.querySelector('[data-orcamento-contagem]');
        if (contagem) contagem.textContent = marcadas.length + (marcadas.length === 1 ? ' selecionado' : ' selecionados');
        const categoriaEl = barra.querySelector('[data-orcamento-categoria]');
        if (categoriaEl) categoriaEl.textContent = comuns ? [...comuns].map(id => nomePorId.get(id)).join(' / ') : '';
    }

    // Campo escondido (outro endereço) não deve travar o envio do formulário: além de escondê-lo,
    // tira o "required" dos campos dele (e devolve quando ele reaparece).
    function sincronizarEnderecoOutro(outro, usarCadastro) {
        outro.hidden = usarCadastro;
        outro.querySelectorAll('input').forEach(input => {
            if (usarCadastro) {
                if (input.required) input.dataset.obrigatorio = '1';
                input.required = false;
            } else if (input.dataset.obrigatorio) {
                input.required = true;
            }
        });
    }

    document.addEventListener('change', event => {
        if (event.target.matches('input[type=checkbox][name=prestadorId]')) atualizar();
        if (event.target.matches('[data-endereco-opcao]')) {
            const bloco = event.target.closest('[data-endereco-execucao]');
            const outro = bloco && bloco.querySelector('[data-endereco-outro]');
            if (outro) sincronizarEnderecoOutro(outro, event.target.value === 'true');
        }
    });

    document.addEventListener('click', event => {
        if (event.target.closest('[data-orcamento-limpar]')) {
            caixas().forEach(c => { c.checked = false; });
            atualizar();
        }
    });

    atualizar();
})();
