// Favoritar sem recarregar a página. A interface responde na hora (otimista) e volta atrás se o servidor recusar.
function aplicarEstadoFavorito(form, favoritado, animar) {
    const button = form.querySelector('.favorite-heart-button');
    const action = form.querySelector('input[name="adicionar"]');
    action.value = String(!favoritado);
    button.classList.toggle('is-favorite', favoritado);
    button.setAttribute('aria-label', favoritado ? 'Remover dos favoritos' : 'Adicionar aos favoritos');
    button.setAttribute('aria-pressed', String(favoritado));
    button.title = favoritado ? 'Remover dos favoritos' : 'Adicionar aos favoritos';
    if (animar) {
        button.classList.remove('is-popping', 'is-unpopping');
        void button.offsetWidth; // reinicia a animação em cliques seguidos
        button.classList.add(favoritado ? 'is-popping' : 'is-unpopping');
    }
}

document.addEventListener('animationend', event => {
    const button = event.target.closest && event.target.closest('.favorite-heart-button');
    if (button && (event.animationName === 'heart-pop' || event.animationName === 'heart-unpop')) {
        button.classList.remove('is-popping', 'is-unpopping');
    }
});

document.addEventListener('submit', async event => {
    const form = event.target.closest('form[data-async-favorite]');
    if (!form) return;

    event.preventDefault();
    const button = form.querySelector('.favorite-heart-button');
    const action = form.querySelector('input[name="adicionar"]');
    const feedback = form.querySelector('[data-favorite-feedback]');
    if (!button || !action || form.classList.contains('is-saving')) return;

    const desejado = action.value === 'true';
    const body = new FormData(form); // captura o valor antes da troca otimista
    form.classList.add('is-saving');
    aplicarEstadoFavorito(form, desejado, true);
    if (feedback) { feedback.classList.remove('is-error'); feedback.textContent = 'Atualizando favoritos…'; }

    try {
        const response = await fetch(form.dataset.asyncAction || form.action, {
            method: 'POST',
            body,
            credentials: 'same-origin',
            headers: {Accept: 'application/json', 'X-Requested-With': 'XMLHttpRequest'}
        });
        const result = await response.json();
        if (!response.ok) throw new Error(result.mensagem || 'Não foi possível atualizar os favoritos.');
        const favoritado = Boolean(result.favoritado);
        if (favoritado !== desejado) aplicarEstadoFavorito(form, favoritado, false);
        if (feedback) feedback.textContent = result.mensagem || 'Favoritos atualizados.';
        // Avisa a página (ex.: tela de favoritos remove o cartão quando o item é desfavoritado)
        form.dispatchEvent(new CustomEvent('favorito:alterado', {bubbles: true, detail: {favoritado}}));
    } catch (error) {
        aplicarEstadoFavorito(form, !desejado, false);
        button.classList.add('is-shake');
        setTimeout(() => button.classList.remove('is-shake'), 400);
        if (feedback) {
            feedback.textContent = error.message || 'Não foi possível atualizar os favoritos.';
            feedback.classList.add('is-error');
        }
    } finally {
        form.classList.remove('is-saving');
    }
});
