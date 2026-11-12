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

// Comentários com limite: contador "350/2.000" e corte de qualquer texto acima do limite (digitado, colado ou preenchido
// por extensão). O textarea do navegador já usa "\n" como quebra; o servidor normaliza o "\r\n" enviado para contar igual.
document.querySelectorAll('textarea[data-limite]').forEach(campo => {
    if (campo.dataset.contadorPronto) return; // o painel pode executar este script mais de uma vez
    campo.dataset.contadorPronto = '1';
    const limite = Number(campo.dataset.limite) || 2000;
    const formatar = n => n.toLocaleString('pt-BR');
    campo.maxLength = limite;

    const contador = document.createElement('small');
    contador.className = 'char-count';
    contador.id = (campo.id || 'comentario') + '-contador';
    campo.setAttribute('aria-describedby', [campo.getAttribute('aria-describedby'), contador.id].filter(Boolean).join(' '));
    campo.insertAdjacentElement('afterend', contador);

    const atualizar = () => {
        if (campo.value.length > limite) campo.value = campo.value.slice(0, limite);
        const usados = campo.value.length;
        const perto = usados >= limite * 0.9;
        contador.textContent = `${formatar(usados)}/${formatar(limite)}`;
        contador.title = usados >= limite ? 'Limite de caracteres atingido' : `Restam ${formatar(limite - usados)} caracteres`;
        contador.classList.toggle('is-perto', perto && usados < limite);
        contador.classList.toggle('is-limite', usados >= limite);
        // Leitor de tela: só anuncia perto do limite, para não falar a cada tecla.
        contador.setAttribute('aria-live', perto ? 'polite' : 'off');
    };
    campo.addEventListener('input', atualizar);
    atualizar();
});

// Textos longos (comentários das avaliações, descrição dos serviços): o CSS corta em poucas linhas; quando o texto
// passa disso, aparece "Ver mais". Sem JavaScript o texto aparece inteiro (o corte só vale com a classe "js" no <html>).
// Com data-ver-mais-href, o "Ver mais" é um link para a página completa (ex.: detalhes do serviço) em vez de expandir.
document.querySelectorAll('[data-texto-recolhivel]').forEach(texto => {
    if (texto.dataset.recolhivelPronto) return; // o painel pode executar este script mais de uma vez
    texto.dataset.recolhivelPronto = '1';
    const destino = texto.dataset.verMaisHref;
    const botao = document.createElement(destino ? 'a' : 'button');
    botao.className = 'review-toggle' + (destino ? ' is-link' : '');
    botao.hidden = true;
    texto.insertAdjacentElement('afterend', botao);

    if (destino) {
        botao.href = destino;
        botao.textContent = 'Ver mais';
        const medirLink = () => { botao.hidden = texto.scrollHeight <= texto.clientHeight + 1; };
        medirLink();
        if ('ResizeObserver' in window) new ResizeObserver(medirLink).observe(texto);
        return;
    }
    botao.type = 'button';
    if (texto.id) botao.setAttribute('aria-controls', texto.id);

    const mostrar = expandido => {
        texto.classList.toggle('is-expanded', expandido);
        botao.textContent = expandido ? 'Ver menos' : 'Ver mais';
        botao.setAttribute('aria-expanded', String(expandido));
    };
    // Só mostra o botão quando o texto realmente foi cortado (recalcula se a largura mudar).
    const medir = () => {
        if (texto.classList.contains('is-expanded')) return;
        botao.hidden = texto.scrollHeight <= texto.clientHeight + 1;
    };
    botao.addEventListener('click', () => {
        const expandir = !texto.classList.contains('is-expanded');
        mostrar(expandir);
        if (!expandir && texto.getBoundingClientRect().top < 0) texto.scrollIntoView({block: 'nearest'});
    });
    mostrar(false);
    medir();
    if ('ResizeObserver' in window) new ResizeObserver(medir).observe(texto);
});
