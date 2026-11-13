// Painel: clique numa fatia do gráfico de pizza (ou na legenda) mostra um card com os atendimentos daquele status.
// A lista desse status é paginada (botão "Ver mais") para não esticar a tela quando houver muitos atendimentos.
// Delegação no document: funciona também quando o painel.js troca o conteúdo sem recarregar a página.
(() => {
    const POR_PAGINA = 8;
    let linhasPendentes = []; // o que ainda falta mostrar da lista aberta no momento

    function mostrarMais(card) {
        const proximas = linhasPendentes.splice(0, POR_PAGINA);
        card.querySelector('tbody').append(...proximas.map(tr => tr.cloneNode(true)));
        const botao = card.querySelector('[data-status-detalhe-vermais]');
        botao.hidden = linhasPendentes.length === 0;
        if (!botao.hidden) botao.querySelector('[data-status-detalhe-restante]').textContent = linhasPendentes.length;
    }

    function abrirDetalhe(gatilho) {
        const card = document.getElementById('status-detalhe');
        const modelo = document.getElementById('status-detalhe-dados');
        if (!card || !modelo) return;
        const status = gatilho.dataset.statusFatia;
        const linhas = [...modelo.content.querySelectorAll('tr')].filter(tr => tr.dataset.status === status);
        linhasPendentes = [...linhas];
        card.querySelector('tbody').replaceChildren();
        mostrarMais(card);
        card.querySelector('[data-status-detalhe-titulo]').textContent = gatilho.dataset.statusTitulo || 'Atendimentos';
        card.querySelector('[data-status-detalhe-sub]').textContent =
            linhas.length + (linhas.length === 1 ? ' atendimento neste período' : ' atendimentos neste período');
        card.hidden = false;
        card.scrollIntoView({behavior: 'smooth', block: 'nearest'});
    }

    document.addEventListener('click', event => {
        const fechar = event.target.closest('[data-status-detalhe-fechar]');
        if (fechar) { document.getElementById('status-detalhe')?.setAttribute('hidden', ''); linhasPendentes = []; return; }
        const vermais = event.target.closest('[data-status-detalhe-vermais]');
        if (vermais) { mostrarMais(vermais.closest('#status-detalhe')); return; }
        const gatilho = event.target.closest('[data-status-fatia]');
        if (gatilho) abrirDetalhe(gatilho);
    });

    // Fatias e legenda também respondem a Enter/Espaço (são focáveis via tabindex).
    document.addEventListener('keydown', event => {
        if (event.key !== 'Enter' && event.key !== ' ') return;
        const gatilho = event.target.closest('[data-status-fatia]');
        if (!gatilho) return;
        event.preventDefault();
        abrirDetalhe(gatilho);
    });
})();
