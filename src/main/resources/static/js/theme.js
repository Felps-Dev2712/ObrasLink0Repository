// Tema claro/escuro: o <head> já aplica o tema salvo antes da pintura (evita piscar); aqui só cuidamos do
// botão (ícone e aria-pressed) e do clique. Delegação no document: funciona mesmo após o painel.js trocar
// o conteúdo sem recarregar a página, já que o botão do tema vive no cabeçalho, fora da área trocada.
(() => {
    const CHAVE = 'obl_tema';

    function sincronizarBotoes(escuro) {
        document.querySelectorAll('[data-theme-toggle]').forEach(botao => {
            botao.setAttribute('aria-pressed', String(escuro));
            botao.setAttribute('aria-label', escuro ? 'Mudar para o tema claro' : 'Mudar para o tema escuro');
        });
    }

    function aplicar(tema) {
        if (tema === 'dark') document.documentElement.setAttribute('data-theme', 'dark');
        else document.documentElement.removeAttribute('data-theme');
        try { localStorage.setItem(CHAVE, tema); } catch (e) { /* modo privado: só não persiste entre visitas */ }
        sincronizarBotoes(tema === 'dark');
    }

    document.addEventListener('click', event => {
        const botao = event.target.closest('[data-theme-toggle]');
        if (!botao) return;
        aplicar(document.documentElement.getAttribute('data-theme') === 'dark' ? 'light' : 'dark');
    });

    sincronizarBotoes(document.documentElement.getAttribute('data-theme') === 'dark');
})();
