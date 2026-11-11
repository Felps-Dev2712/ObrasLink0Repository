/*
 * Select customizado (melhoria progressiva).
 * O <select> nativo continua no formulário: é ele que envia o valor, recebe a validação "required"
 * e dispara "change" (inclusive os onchange="this.form.submit()" das telas). Este script só desenha
 * um botão + lista acessível por cima dele. Use data-native no <select> para manter o padrão do navegador.
 */
(() => {
    if (!('MutationObserver' in window) || !Element.prototype.closest) return;

    let uid = 0;
    let aberto = null; // instância com a lista aberta

    const chevron = '<svg class="cselect-chevron" viewBox="0 0 24 24" aria-hidden="true"><path d="m6 9 6 6 6-6"/></svg>';
    const check = '<svg class="cselect-check" viewBox="0 0 24 24" aria-hidden="true"><path d="M20 6 9 17l-5-5"/></svg>';

    function enhance(select) {
        if (select.dataset.enhanced || select.hasAttribute('data-native') || select.size > 1 && !select.multiple) return;
        select.dataset.enhanced = '1';
        const id = 'cs' + (++uid);
        const multiple = select.multiple;

        const wrap = document.createElement('div');
        wrap.className = 'cselect' + (multiple ? ' cselect-multi' : '');
        select.parentNode.insertBefore(wrap, select);

        const button = document.createElement('button');
        button.type = 'button';
        button.className = 'cselect-trigger';
        button.id = id + '-btn';
        button.setAttribute('role', 'combobox');
        button.setAttribute('aria-haspopup', 'listbox');
        button.setAttribute('aria-expanded', 'false');
        button.setAttribute('aria-controls', id + '-list');
        button.innerHTML = '<span class="cselect-value"></span>' + chevron;
        wrap.appendChild(button);
        wrap.appendChild(select);
        select.classList.add('cselect-native');
        select.tabIndex = -1;
        select.setAttribute('aria-hidden', 'true');

        // Rótulo: <label for> passa a apontar para o botão; <label> envolvente já rotula o botão (primeiro controle).
        if (select.id) {
            const label = document.querySelector('label[for="' + CSS.escape(select.id) + '"]');
            if (label) { label.htmlFor = button.id; label.id = label.id || id + '-label'; button.setAttribute('aria-labelledby', label.id + ' ' + button.id); }
        }
        if (!button.hasAttribute('aria-labelledby') && select.getAttribute('aria-label')) button.setAttribute('aria-label', select.getAttribute('aria-label'));

        const list = document.createElement('div');
        list.className = 'cselect-list';
        list.id = id + '-list';
        list.setAttribute('role', 'listbox');
        list.tabIndex = -1;
        if (multiple) list.setAttribute('aria-multiselectable', 'true');
        list.hidden = true;
        document.body.appendChild(list);

        let ativo = -1;
        let busca = '';
        let buscaTimer = null;

        const opcoes = () => [...list.querySelectorAll('[role="option"]')];

        function construirLista() {
            list.innerHTML = '';
            [...select.options].forEach((opt, i) => {
                const item = document.createElement('div');
                item.className = 'cselect-option' + (opt.value === '' ? ' is-placeholder' : '');
                item.id = id + '-opt-' + i;
                item.setAttribute('role', 'option');
                item.dataset.index = i;
                item.innerHTML = check + '<span></span>';
                item.lastChild.textContent = opt.textContent.trim();
                if (opt.disabled) item.setAttribute('aria-disabled', 'true');
                list.appendChild(item);
            });
            sincronizar();
        }

        function sincronizar() {
            const selecionadas = [...select.options].filter(o => o.selected);
            opcoes().forEach(item => item.setAttribute('aria-selected', String(select.options[item.dataset.index].selected)));
            const valor = button.querySelector('.cselect-value');
            if (multiple) {
                const nomes = selecionadas.map(o => o.textContent.trim());
                valor.textContent = nomes.length === 0 ? (select.dataset.placeholder || 'Selecione...')
                    : nomes.length <= 2 ? nomes.join(', ') : nomes.length + ' selecionadas';
                wrap.classList.toggle('is-empty', nomes.length === 0);
            } else {
                const opt = select.options[select.selectedIndex];
                valor.textContent = opt ? opt.textContent.trim() : (select.dataset.placeholder || 'Selecione...');
                wrap.classList.toggle('is-empty', !opt || opt.value === '');
            }
            button.disabled = select.disabled;
            wrap.classList.toggle('is-disabled', select.disabled);
            if (select.validity.valid) wrap.classList.remove('is-invalid'); // validity não dispara o evento "invalid"
        }

        function posicionar() {
            const r = button.getBoundingClientRect();
            const alturaMax = 288;
            const abaixo = window.innerHeight - r.bottom - 8;
            const acima = r.top - 8;
            const paraCima = abaixo < Math.min(alturaMax, list.scrollHeight) && acima > abaixo;
            list.style.minWidth = r.width + 'px';
            list.style.left = Math.max(8, Math.min(r.left, window.innerWidth - Math.max(r.width, list.offsetWidth) - 8)) + 'px';
            list.style.maxHeight = Math.min(alturaMax, paraCima ? acima : abaixo) + 'px';
            if (paraCima) { list.style.top = ''; list.style.bottom = (window.innerHeight - r.top + 6) + 'px'; }
            else { list.style.bottom = ''; list.style.top = (r.bottom + 6) + 'px'; }
            list.classList.toggle('is-up', paraCima);
        }

        function marcarAtivo(i, rolar = true) {
            const itens = opcoes();
            if (!itens.length) return;
            ativo = Math.max(0, Math.min(i, itens.length - 1));
            itens.forEach((item, n) => item.classList.toggle('is-active', n === ativo));
            button.setAttribute('aria-activedescendant', itens[ativo].id);
            if (rolar) itens[ativo].scrollIntoView({block: 'nearest'});
        }

        function abrir() {
            if (select.disabled) return;
            if (aberto && aberto !== api) aberto.fechar();
            list.hidden = false;
            wrap.classList.add('is-open');
            button.setAttribute('aria-expanded', 'true');
            posicionar();
            const atual = multiple ? opcoes().findIndex(item => item.getAttribute('aria-selected') === 'true') : select.selectedIndex;
            marcarAtivo(atual >= 0 ? atual : 0);
            aberto = api;
        }

        function fechar(focar) {
            if (list.hidden) return;
            list.hidden = true;
            wrap.classList.remove('is-open');
            button.setAttribute('aria-expanded', 'false');
            button.removeAttribute('aria-activedescendant');
            if (aberto === api) aberto = null;
            if (focar) button.focus();
        }

        function escolher(i) {
            const opt = select.options[i];
            if (!opt || opt.disabled) return;
            if (multiple) opt.selected = !opt.selected;
            else { if (select.selectedIndex === i) { fechar(true); return; } select.selectedIndex = i; }
            sincronizar();
            select.dispatchEvent(new Event('input', {bubbles: true}));
            select.dispatchEvent(new Event('change', {bubbles: true}));
            if (!multiple) fechar(true);
        }

        button.addEventListener('click', () => (list.hidden ? abrir() : fechar()));
        button.addEventListener('keydown', e => {
            const itens = opcoes();
            switch (e.key) {
                case 'ArrowDown': e.preventDefault(); list.hidden ? abrir() : marcarAtivo(ativo + 1); break;
                case 'ArrowUp': e.preventDefault(); list.hidden ? abrir() : marcarAtivo(ativo - 1); break;
                case 'Home': if (!list.hidden) { e.preventDefault(); marcarAtivo(0); } break;
                case 'End': if (!list.hidden) { e.preventDefault(); marcarAtivo(itens.length - 1); } break;
                case 'Enter': case ' ':
                    if (e.key === ' ' && busca) { break; }
                    e.preventDefault();
                    list.hidden ? abrir() : escolher(Number(itens[ativo].dataset.index));
                    break;
                case 'Escape': if (!list.hidden) { e.preventDefault(); fechar(true); } break;
                case 'Tab': fechar(); break;
                default:
                    if (e.key.length === 1 && !e.ctrlKey && !e.metaKey && !e.altKey) {
                        busca += e.key.toLowerCase();
                        clearTimeout(buscaTimer);
                        buscaTimer = setTimeout(() => { busca = ''; }, 600);
                        const n = itens.findIndex(item => item.textContent.trim().toLowerCase().startsWith(busca));
                        if (n >= 0) { if (list.hidden) abrir(); marcarAtivo(n); }
                    }
            }
        });

        button.addEventListener('blur', e => { if (!list.contains(e.relatedTarget)) fechar(); });
        list.addEventListener('mousedown', e => e.preventDefault()); // mantém o foco no botão
        list.addEventListener('click', e => {
            const item = e.target.closest('[role="option"]');
            if (item) escolher(Number(item.dataset.index));
        });
        list.addEventListener('mousemove', e => {
            const item = e.target.closest('[role="option"]');
            if (item) marcarAtivo(opcoes().indexOf(item), false);
        });

        // Validação nativa: destaca o campo e leva o foco ao botão visível.
        select.addEventListener('invalid', () => { wrap.classList.add('is-invalid'); button.focus(); });
        select.addEventListener('focus', () => button.focus());
        select.addEventListener('change', sincronizar);
        if (select.form) select.form.addEventListener('reset', () => setTimeout(sincronizar));

        // Mudanças feitas por outros scripts (disabled/required no cadastro, opções novas).
        new MutationObserver(construirLista).observe(select, {childList: true, subtree: true});
        new MutationObserver(sincronizar).observe(select, {attributes: true, attributeFilter: ['disabled', 'required']});

        const api = {fechar, posicionar, wrap, list};
        construirLista();
    }

    function enhanceAll(root) {
        (root.matches && root.matches('select') ? [root] : [...root.querySelectorAll('select')]).forEach(enhance);
    }

    document.addEventListener('mousedown', e => {
        if (aberto && !aberto.wrap.contains(e.target) && !aberto.list.contains(e.target)) aberto.fechar();
    });
    window.addEventListener('scroll', () => aberto && aberto.posicionar(), true);
    window.addEventListener('resize', () => aberto && aberto.fechar());

    function iniciar() {
        enhanceAll(document);
        new MutationObserver(mutacoes => mutacoes.forEach(m => m.addedNodes.forEach(n => {
            if (n.nodeType === 1 && !n.closest('.cselect-list')) enhanceAll(n);
        }))).observe(document.body, {childList: true, subtree: true});
    }
    if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', iniciar);
    else iniciar();
})();
