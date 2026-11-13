// Mascote Tchuros: aparece na home a cada login (não só na primeira vez), graças a um cookie bem curto que o
// servidor grava ao autenticar (ver ContaVinculadaAuthenticationSuccessHandler). Lido uma vez e apagado na hora,
// pra não reaparecer só por recarregar a página sem logar de novo.
(() => {
    const COOKIE_LOGIN = 'obl_tchuros_login';

    function lerCookie(nome) {
        return document.cookie.split('; ').some(par => par === nome + '=1');
    }

    function apagarCookie(nome) {
        document.cookie = nome + '=; Max-Age=0; path=/';
    }

    const mascote = document.getElementById('tchuros');
    if (mascote && lerCookie(COOKIE_LOGIN)) {
        mascote.hidden = false;
        apagarCookie(COOKIE_LOGIN);
    }

    document.addEventListener('click', event => {
        const botao = event.target.closest('[data-tchuros-fechar]');
        if (!botao) return;
        const alvo = botao.closest('#tchuros');
        if (alvo) alvo.hidden = true;
    });
})();
