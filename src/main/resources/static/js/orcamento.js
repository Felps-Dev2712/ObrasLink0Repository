// Seleção de vários prestadores da mesma categoria, na vitrine, para pedir orçamento em conjunto.
// Ao marcar o primeiro, trava a categoria: os checkboxes de outras categorias ficam desabilitados
// até a seleção esvaziar de novo. A barra flutuante só aparece com pelo menos um marcado.
// Delegação no document (como o dashboard.js): funciona mesmo quando o painel.js troca o conteúdo
// sem recarregar a página, já que a #orcamento-form só existe na tela de prestadores.
(() => {
    function caixas() {
        return [...document.querySelectorAll('input[type=checkbox][name=prestadorId]')];
    }

    function atualizar() {
        const barra = document.getElementById('orcamento-form');
        if (!barra) return;
        const marcadas = caixas().filter(c => c.checked);
        const categoriaId = marcadas[0] ? marcadas[0].dataset.categoriaId : null;

        caixas().forEach(c => {
            c.disabled = marcadas.length > 0 && c.dataset.categoriaId !== categoriaId && !c.checked;
            c.closest('.pro-card-select')?.classList.toggle('is-disabled', c.disabled);
        });

        barra.hidden = marcadas.length === 0;
        const contagem = barra.querySelector('[data-orcamento-contagem]');
        if (contagem) contagem.textContent = marcadas.length + (marcadas.length === 1 ? ' selecionado' : ' selecionados');
        const categoriaEl = barra.querySelector('[data-orcamento-categoria]');
        if (categoriaEl) categoriaEl.textContent = marcadas[0] ? marcadas[0].dataset.categoriaNome : '';
    }

    document.addEventListener('change', event => {
        if (event.target.matches('input[type=checkbox][name=prestadorId]')) atualizar();
        if (event.target.matches('[data-endereco-opcao]')) {
            const bloco = event.target.closest('[data-endereco-execucao]');
            const outro = bloco && bloco.querySelector('[data-endereco-outro]');
            if (outro) outro.hidden = event.target.value === 'true';
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
