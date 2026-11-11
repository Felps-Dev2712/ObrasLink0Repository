// Fotos: envio automático, prévia com arrastar e soltar, validação no navegador e visualizador ampliado.
// Usa delegação de eventos no document, então funciona também no conteúdo carregado dentro do painel.
(() => {
    const TIPOS = ['image/jpeg', 'image/png', 'image/webp'];
    const MAX_BYTES = 5 * 1024 * 1024;
    const problemaDoArquivo = f => !TIPOS.includes(f.type) ? `${f.name}: use JPG, PNG ou WebP.`
        : f.size > MAX_BYTES ? `${f.name}: maior que 5 MB.` : null;

    // Formulários de uma foto só (perfil e capa): envia assim que a foto é escolhida.
    document.addEventListener('change', event => {
        const input = event.target;
        if (!(input instanceof HTMLInputElement) || input.type !== 'file') return;
        const autoForm = input.closest('form[data-auto-upload]');
        if (autoForm && input.files.length) {
            const problema = problemaDoArquivo(input.files[0]);
            if (problema) { alert(problema); input.value = ''; return; }
            autoForm.classList.add('is-uploading');
            if (autoForm.requestSubmit) autoForm.requestSubmit(); else autoForm.submit();
            return;
        }
        const zona = input.closest('[data-dropzone]');
        if (zona) mostrarPrevia(zona, input);
    });

    function mostrarPrevia(zona, input) {
        const campo = zona.parentElement;
        const caixa = campo.querySelector('[data-photo-preview]');
        const erro = campo.querySelector('[data-photo-error]');
        const max = Number(zona.dataset.max || 6);
        const arquivos = [...input.files];
        caixa.innerHTML = '';
        erro.hidden = true;
        const problemas = arquivos.map(problemaDoArquivo).filter(Boolean);
        if (arquivos.length > max) problemas.unshift(`Você pode enviar até ${max} foto(s) agora.`);
        if (problemas.length) {
            erro.textContent = problemas.join(' ');
            erro.hidden = false;
            input.value = '';
            zona.querySelector('strong').textContent = 'Clique ou arraste fotos aqui';
            return;
        }
        arquivos.forEach(arquivo => {
            const url = URL.createObjectURL(arquivo);
            const fig = document.createElement('figure');
            fig.className = 'photo-item is-new';
            const img = document.createElement('img');
            img.alt = '';
            img.src = url;
            img.onload = () => URL.revokeObjectURL(url);
            fig.appendChild(img);
            caixa.appendChild(fig);
        });
        zona.querySelector('strong').textContent = arquivos.length
            ? `${arquivos.length} ${arquivos.length === 1 ? 'foto selecionada' : 'fotos selecionadas'} — salve para publicar`
            : 'Clique ou arraste fotos aqui';
    }

    // Arrastar e soltar na área de fotos
    ['dragenter', 'dragover'].forEach(tipo => document.addEventListener(tipo, event => {
        const zona = event.target.closest && event.target.closest('[data-dropzone]');
        if (!zona) return;
        event.preventDefault();
        zona.classList.add('is-drag');
    }));
    ['dragleave', 'drop'].forEach(tipo => document.addEventListener(tipo, event => {
        const zona = event.target.closest && event.target.closest('[data-dropzone]');
        if (!zona) return;
        event.preventDefault();
        zona.classList.remove('is-drag');
        if (tipo === 'drop' && event.dataTransfer.files.length) {
            const input = zona.querySelector('input[type="file"]');
            if (input.disabled) return;
            input.files = event.dataTransfer.files;
            mostrarPrevia(zona, input);
        }
    }));

    // Visualizador: links [data-lightbox] (agrupados por data-lightbox-group) abrem a foto ampliada
    let dialogo, imagem, contador, grupo = [], atual = 0;
    function criarDialogo() {
        dialogo = document.createElement('dialog');
        dialogo.className = 'lightbox';
        dialogo.setAttribute('aria-label', 'Foto ampliada');
        dialogo.innerHTML = '<button type="button" class="lightbox-close" aria-label="Fechar">✕</button>'
            + '<button type="button" class="lightbox-nav lightbox-prev" aria-label="Foto anterior">‹</button>'
            + '<figure><img alt=""><figcaption></figcaption></figure>'
            + '<button type="button" class="lightbox-nav lightbox-next" aria-label="Próxima foto">›</button>';
        document.body.appendChild(dialogo);
        imagem = dialogo.querySelector('img');
        contador = dialogo.querySelector('figcaption');
        dialogo.querySelector('.lightbox-close').addEventListener('click', () => dialogo.close());
        dialogo.querySelector('.lightbox-prev').addEventListener('click', () => mostrar(atual - 1));
        dialogo.querySelector('.lightbox-next').addEventListener('click', () => mostrar(atual + 1));
        dialogo.addEventListener('click', event => { if (event.target === dialogo) dialogo.close(); });
        dialogo.addEventListener('keydown', event => {
            if (event.key === 'ArrowLeft') mostrar(atual - 1);
            if (event.key === 'ArrowRight') mostrar(atual + 1);
        });
    }
    function mostrar(indice) {
        atual = (indice + grupo.length) % grupo.length;
        imagem.src = grupo[atual].href;
        contador.textContent = grupo.length > 1 ? `${atual + 1} de ${grupo.length}` : '';
        dialogo.querySelectorAll('.lightbox-nav').forEach(b => { b.hidden = grupo.length < 2; });
    }
    document.addEventListener('click', event => {
        const link = event.target.closest && event.target.closest('a[data-lightbox]');
        if (!link || event.ctrlKey || event.metaKey) return;
        event.preventDefault();
        if (!dialogo) criarDialogo();
        const nome = link.dataset.lightboxGroup;
        grupo = nome ? [...document.querySelectorAll(`a[data-lightbox][data-lightbox-group="${CSS.escape(nome)}"]`)] : [link];
        // Remove links repetidos da mesma foto (capa + miniatura)
        grupo = grupo.filter((a, i, todos) => todos.findIndex(b => b.href === a.href) === i);
        mostrar(Math.max(0, grupo.findIndex(a => a.href === link.href)));
        dialogo.showModal();
    });
})();
