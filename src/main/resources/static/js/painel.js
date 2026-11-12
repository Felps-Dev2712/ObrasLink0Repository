// Painel do prestador: os itens do menu lateral (e os links/formulários dentro do painel) carregam
// a tela no lado direito, sem recarregar a página. Sem JavaScript tudo continua funcionando por links normais.
(() => {
    const shell = document.querySelector('.app-shell');
    if (!shell || !window.fetch || !window.DOMParser || !history.pushState) return;
    const area = shell.querySelector('.app-main');
    const linksMenu = [...shell.querySelectorAll('.sidebar-nav a')];
    // Rotas que sempre abrem fora do painel (sessão, arquivos e documentos)
    const FORA = /^\/(login|logout|register|termos|privacidade|imagens\/|certificacoes\/\d+\/documento)/;
    const SCRIPTS_DO_LAYOUT = /\/js\/(dados-cadastrais|favoritos|selects|midia|confirmacao|localizacao|painel|login)\.js$/;

    const interno = url => url.origin === location.origin && !FORA.test(url.pathname);

    function marcarAtivo(caminho) {
        linksMenu.forEach(link => {
            const alvo = new URL(link.href).pathname;
            const ativo = alvo === '/dashboard' ? caminho === '/dashboard' : caminho === alvo || caminho.startsWith(alvo + '/');
            link.classList.toggle('active', ativo);
            if (ativo) link.setAttribute('aria-current', 'page'); else link.removeAttribute('aria-current');
        });
    }

    function executarScripts(doc) {
        doc.querySelectorAll('body script').forEach(original => {
            if (original.hasAttribute('data-layout')) return;
            if (original.src && SCRIPTS_DO_LAYOUT.test(new URL(original.src, location.href).pathname)) return;
            const script = document.createElement('script');
            if (original.src) script.src = original.src; else script.textContent = original.textContent;
            area.appendChild(script);
        });
    }

    async function carregar(url, opcoes = {}, empilhar = true) {
        shell.classList.add('is-loading');
        area.setAttribute('aria-busy', 'true');
        try {
            const resposta = await fetch(url, {credentials: 'same-origin', ...opcoes});
            const final = new URL(resposta.url);
            const tipo = resposta.headers.get('content-type') || '';
            if (!interno(final) || !tipo.includes('text/html')) { location.href = final.href; return; }
            const doc = new DOMParser().parseFromString(await resposta.text(), 'text/html');
            const painel = doc.querySelector('.app-main');
            const principal = painel || doc.querySelector('main#conteudo') || doc.querySelector('main');
            if (!principal) { location.href = final.href; return; }

            area.replaceChildren();
            if (painel) {
                area.append(...[...painel.childNodes].map(no => document.importNode(no, true)));
            } else {
                const vista = document.createElement('div');
                vista.className = 'painel-view ' + principal.className;
                vista.innerHTML = principal.innerHTML;
                area.appendChild(vista);
            }
            document.title = doc.title;
            if (empilhar) history.pushState({painel: true}, '', final.pathname + final.search + final.hash);
            marcarAtivo(final.pathname);
            executarScripts(doc);
            if (window.ObraLink) window.ObraLink.iniciar(area);
            window.scrollTo({top: 0});
            const titulo = area.querySelector('h1');
            if (titulo) { titulo.tabIndex = -1; titulo.focus({preventScroll: true}); }
        } catch (erro) {
            location.href = url; // rede falhou: carrega a página inteira
        } finally {
            shell.classList.remove('is-loading');
            area.removeAttribute('aria-busy');
        }
    }

    shell.addEventListener('click', event => {
        const link = event.target.closest('a');
        if (!link || event.defaultPrevented || event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
        if ((link.target && link.target !== '_self') || link.hasAttribute('download') || link.hasAttribute('data-lightbox')
            || link.closest('.brand') || link.hasAttribute('data-no-painel')) return;
        const url = new URL(link.href, location.href);
        if (!interno(url)) return;
        if (url.pathname === location.pathname && url.search === location.search && url.hash) return; // âncora na mesma tela
        event.preventDefault();
        carregar(url.href);
    });

    area.addEventListener('submit', event => {
        const form = event.target;
        if (event.defaultPrevented || form.hasAttribute('data-async-favorite') || form.hasAttribute('data-no-painel')) return;
        const acao = new URL(form.getAttribute('action') || location.href, location.href);
        if (!interno(acao)) return;
        event.preventDefault();
        if ((form.method || 'get').toLowerCase() === 'get') {
            acao.search = new URLSearchParams(new FormData(form)).toString();
            carregar(acao.href);
            return;
        }
        const dados = new FormData(form);
        if (event.submitter && event.submitter.name) dados.append(event.submitter.name, event.submitter.value);
        carregar(acao.href, {method: 'POST', body: dados});
    });

    history.replaceState({painel: true}, '');
    window.addEventListener('popstate', () => carregar(location.href, {}, false));
})();
