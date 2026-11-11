document.addEventListener('submit', async event => {
    const form = event.target.closest('form[data-async-favorite]');
    if (!form) return;

    event.preventDefault();
    const button = form.querySelector('.favorite-heart-button');
    const action = form.querySelector('input[name="adicionar"]');
    const feedback = form.querySelector('[data-favorite-feedback]');
    if (!button || !action || button.disabled) return;

    button.disabled = true;
    form.classList.add('is-saving');
    if (feedback) feedback.textContent = 'Atualizando favoritos…';

    try {
        const response = await fetch(form.dataset.asyncAction || form.action, {
            method: 'POST',
            body: new FormData(form),
            credentials: 'same-origin',
            headers: {
                Accept: 'application/json',
                'X-Requested-With': 'XMLHttpRequest'
            }
        });
        const result = await response.json();
        if (!response.ok) throw new Error(result.mensagem || 'Não foi possível atualizar os favoritos.');

        const favoritado = Boolean(result.favoritado);
        action.value = String(!favoritado);
        button.classList.toggle('is-favorite', favoritado);
        button.setAttribute('aria-label', favoritado ? 'Remover dos favoritos' : 'Adicionar aos favoritos');
        button.title = favoritado ? 'Remover dos favoritos' : 'Adicionar aos favoritos';
        if (feedback) {
            feedback.textContent = result.mensagem || 'Favoritos atualizados.';
            feedback.classList.remove('is-error');
        }
    } catch (error) {
        if (feedback) {
            feedback.textContent = error.message || 'Não foi possível atualizar os favoritos.';
            feedback.classList.add('is-error');
        }
    } finally {
        button.disabled = false;
        form.classList.remove('is-saving');
    }
});
