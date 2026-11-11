(() => {
    const password = document.getElementById('password');
    const passwordToggle = document.querySelector('[data-password-toggle]');
    if (password && passwordToggle) {
        passwordToggle.addEventListener('click', () => {
            const visible = password.type === 'password';
            password.type = visible ? 'text' : 'password';
            passwordToggle.textContent = visible ? 'Ocultar' : 'Mostrar';
            passwordToggle.setAttribute('aria-label', visible ? 'Ocultar senha' : 'Mostrar senha');
            passwordToggle.setAttribute('aria-pressed', String(visible));
        });
    }
    const carousel = document.querySelector('[data-best-rated-carousel]');
    if (!carousel) return;
    const cards = [...carousel.querySelectorAll('.best-rated-row')];
    const pause = carousel.querySelector('[data-carousel-pause]');
    const count = carousel.querySelector('[data-carousel-count]');
    const motion = window.matchMedia('(prefers-reduced-motion: reduce)');
    let paused = motion.matches;
    let current = 0;
    function show(index) {
        current = (index + cards.length) % cards.length;
        cards.forEach((card, i) => {
            card.hidden = i !== current;
            card.setAttribute('aria-hidden', String(i !== current));
            if (i !== current) card.setAttribute('tabindex', '-1');
            else card.removeAttribute('tabindex');
        });
        count.textContent = `${current + 1} / ${cards.length}`;
    }
    function label() {
        pause.textContent = paused ? 'Reproduzir' : 'Pausar';
        pause.setAttribute('aria-label', paused ? 'Reproduzir carrossel' : 'Pausar carrossel');
    }
    if (cards.length < 2) {
        carousel.querySelectorAll('button').forEach(button => { button.hidden = true; });
        show(0);
        return;
    }
    carousel.querySelector('[data-carousel-prev]').addEventListener('click', () => show(current - 1));
    carousel.querySelector('[data-carousel-next]').addEventListener('click', () => show(current + 1));
    pause.addEventListener('click', () => { paused = !paused; label(); });
    motion.addEventListener('change', event => { paused = event.matches; label(); });
    show(0);
    label();
    setInterval(() => {
        if (!paused && !document.hidden && !carousel.matches(':hover') && !carousel.contains(document.activeElement)) show(current + 1);
    }, 5000);
})();
