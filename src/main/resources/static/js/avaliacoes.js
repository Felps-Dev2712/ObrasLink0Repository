// Cada grupo de radios altera somente o próprio critério. O envio é feito pelo botão do formulário.
document.querySelectorAll('[data-rating-criterion]').forEach(group => {
    const radios = Array.from(group.querySelectorAll('input[type="radio"]'));
    const status = group.querySelector('[data-criterion-score]');
    const update = () => {
        const selected = radios.find(radio => radio.checked);
        radios.forEach(radio => radio.closest('label').classList.toggle('rating-selected',
            !!selected && Number(radio.value) <= Number(selected.value)));
        status.textContent = selected ? `${selected.value} de 5 estrelas` : 'Selecione uma nota';
    };
    radios.forEach(radio => radio.addEventListener('change', update));
    update();
});
