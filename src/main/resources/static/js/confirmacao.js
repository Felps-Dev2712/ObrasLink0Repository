// Confirmação antes de ações que não podem ser desfeitas: <form data-confirmar data-confirmar-titulo="…"
// data-confirmar-texto="…" data-confirmar-botao="…">. Abre um diálogo do próprio sistema (no lugar do confirm() do navegador).
// Também alterna os formulários de "Alterar data" ([data-alternar-reagendar] com aria-controls).
// Usa delegação no document (fase de captura): funciona nas páginas e no conteúdo carregado dentro do painel.
(() => {
    let dialogo, formPendente;

    function criarDialogo() {
        dialogo = document.createElement('dialog');
        dialogo.className = 'confirm-dialog';
        dialogo.setAttribute('aria-labelledby', 'confirmar-titulo');
        dialogo.setAttribute('aria-describedby', 'confirmar-texto');
        const titulo = document.createElement('h2');
        titulo.id = 'confirmar-titulo';
        const texto = document.createElement('p');
        texto.id = 'confirmar-texto';
        const acoes = document.createElement('div');
        acoes.className = 'confirm-dialog-actions';
        const cancelar = document.createElement('button');
        cancelar.type = 'button';
        cancelar.className = 'btn btn-ghost';
        cancelar.textContent = 'Cancelar';
        const confirmar = document.createElement('button');
        confirmar.type = 'button';
        confirmar.className = 'btn btn-primary';
        confirmar.dataset.confirmarOk = '';
        acoes.append(cancelar, confirmar);
        dialogo.append(titulo, texto, acoes);
        document.body.append(dialogo);

        cancelar.addEventListener('click', () => dialogo.close());
        dialogo.addEventListener('close', () => { formPendente = null; });
        // Clique fora do conteúdo fecha
        dialogo.addEventListener('click', e => { if (e.target === dialogo) dialogo.close(); });
        confirmar.addEventListener('click', () => {
            const form = formPendente;
            dialogo.close();
            if (!form) return;
            form.dataset.confirmado = '1';
            if (form.requestSubmit) form.requestSubmit(); else form.submit();
        });
    }

    document.addEventListener('submit', event => {
        const form = event.target;
        if (!(form instanceof HTMLFormElement) || !form.hasAttribute('data-confirmar')) return;
        if (form.dataset.confirmado === '1') { delete form.dataset.confirmado; return; } // já confirmado: segue o envio
        event.preventDefault();
        event.stopImmediatePropagation();
        if (!dialogo) criarDialogo();
        formPendente = form;
        dialogo.querySelector('h2').textContent = form.dataset.confirmarTitulo || 'Confirmar ação?';
        dialogo.querySelector('p').textContent = form.dataset.confirmarTexto || 'Esta ação não pode ser desfeita.';
        dialogo.querySelector('[data-confirmar-ok]').textContent = form.dataset.confirmarBotao || 'Confirmar';
        if (typeof dialogo.showModal === 'function') dialogo.showModal();
        else if (window.confirm(form.dataset.confirmarTexto || 'Confirmar?')) { form.dataset.confirmado = '1'; form.submit(); }
        dialogo.querySelector('.btn-ghost').focus(); // foco na opção segura
    }, true);

    document.addEventListener('click', event => {
        const botao = event.target.closest && event.target.closest('[data-alternar-reagendar]');
        if (!botao) return;
        const form = document.getElementById(botao.getAttribute('aria-controls'));
        if (!form) return;
        const abrir = form.hidden;
        form.hidden = !abrir;
        document.querySelectorAll(`[data-alternar-reagendar][aria-controls="${CSS.escape(form.id)}"]`)
            .forEach(b => { if (b.hasAttribute('aria-expanded')) b.setAttribute('aria-expanded', String(abrir)); });
        if (abrir) form.querySelector('input')?.focus();
        else document.querySelector(`[data-alternar-reagendar][aria-expanded][aria-controls="${CSS.escape(form.id)}"]`)?.focus();
    });
})();
