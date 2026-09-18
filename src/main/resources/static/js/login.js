(() => {
    const carousel = document.querySelector('.featured-providers');
    if (!carousel) return;
    const cards = [...carousel.querySelectorAll('.featured-provider')];
    const pause = carousel.querySelector('[data-carousel-pause]');
    const motion = window.matchMedia('(prefers-reduced-motion: reduce)');
    let paused = motion.matches;
    let current = 0;
    function show(index) {
        current = (index + cards.length) % cards.length;
        cards.forEach((card, i) => { card.hidden = i !== current; });
    }
    function label() {
        pause.textContent = paused ? 'Reproduzir' : 'Pausar';
        pause.setAttribute('aria-label', paused ? 'Reproduzir carrossel' : 'Pausar carrossel');
    }
    if (cards.length < 2) {
        carousel.querySelectorAll('button').forEach(button => { button.hidden = true; });
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
