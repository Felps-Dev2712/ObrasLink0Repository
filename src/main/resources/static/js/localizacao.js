// Localização com OpenStreetMap (Leaflet): seletor de endereço com mapa, mapa da área do prestador e mapa da lista.
// O Leaflet só é carregado quando a página tem um mapa. ObraLinkMapas.iniciar(raiz) também roda no conteúdo do painel.
(() => {
    const LEAFLET_CSS = 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/leaflet.min.css';
    const LEAFLET_JS = 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/leaflet.min.js';
    const TILES = 'https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png';
    const ATRIBUICAO = '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>';
    const CENTRO_PADRAO = [-23.5505, -46.6333]; // São Paulo
    let carregando = null;

    function carregarLeaflet() {
        if (window.L) return Promise.resolve(window.L);
        if (carregando) return carregando;
        carregando = new Promise((ok, erro) => {
            const css = document.createElement('link');
            css.rel = 'stylesheet';
            css.href = LEAFLET_CSS;
            document.head.appendChild(css);
            const js = document.createElement('script');
            js.src = LEAFLET_JS;
            js.onload = () => ok(window.L);
            js.onerror = erro;
            document.head.appendChild(js);
        });
        return carregando;
    }

    function novoMapa(el, opcoes = {}) {
        const mapa = L.map(el, {scrollWheelZoom: false, ...opcoes});
        L.tileLayer(TILES, {maxZoom: 19, attribution: ATRIBUICAO}).addTo(mapa);
        // O mapa pode nascer escondido (cadastro, painel): corrige o tamanho quando aparecer
        if ('ResizeObserver' in window) new ResizeObserver(() => mapa.invalidateSize()).observe(el);
        return mapa;
    }

    const pino = (classe = '') => L.divIcon({className: 'map-pin ' + classe, html: '<span></span>', iconSize: [28, 28], iconAnchor: [14, 28], popupAnchor: [0, -26]});
    const casa = () => L.divIcon({className: 'map-home', html: '<span>⌂</span>', iconSize: [30, 30], iconAnchor: [15, 15]});

    // ---------- Seletor de endereço (cadastro, minha conta, formulários do admin) ----------
    function iniciarSeletor(caixa) {
        if (caixa.dataset.geoPronto) return;
        caixa.dataset.geoPronto = '1';
        const campo = nome => caixa.querySelector(`[data-geo="${nome}"]`);
        const [cep, rua, numero, bairro, cidade, uf, lat, lng, raio] =
            ['cep', 'logradouro', 'numero', 'bairro', 'cidade', 'estado', 'lat', 'lng', 'raio'].map(campo);
        const status = caixa.querySelector('[data-geo-status]');
        const elMapa = caixa.querySelector('[data-geo-map]');
        let mapa, marcador, circulo;

        const avisar = (texto, tipo = '') => { if (status) { status.textContent = texto; status.dataset.tipo = tipo; } };
        const temPonto = () => lat.value && lng.value;

        function desenhar(zoom) {
            if (!mapa) return;
            if (!temPonto()) { if (marcador) { marcador.remove(); marcador = null; } if (circulo) { circulo.remove(); circulo = null; } return; }
            const ponto = [Number(lat.value), Number(lng.value)];
            if (!marcador) {
                marcador = L.marker(ponto, {draggable: true, icon: pino(), keyboard: true, title: 'Arraste para ajustar'}).addTo(mapa);
                marcador.on('dragend', () => definir(marcador.getLatLng(), 'Ponto ajustado por você.'));
            } else marcador.setLatLng(ponto);
            if (raio) {
                const km = Math.max(1, Number(raio.value || 15));
                if (!circulo) circulo = L.circle(ponto, {radius: km * 1000, color: '#EA580C', weight: 2, fillOpacity: .08}).addTo(mapa);
                else circulo.setLatLng(ponto).setRadius(km * 1000);
                mapa.fitBounds(circulo.getBounds(), {padding: [16, 16]});
            } else if (zoom) mapa.setView(ponto, 15);
        }

        function definir(latlng, mensagem) {
            lat.value = Number(latlng.lat).toFixed(6);
            lng.value = Number(latlng.lng).toFixed(6);
            avisar(mensagem, 'ok');
            desenhar(true);
        }

        async function buscarCoordenadas() {
            if (!cidade.value.trim() || !uf.value.trim()) { avisar('Preencha CEP, número, cidade e estado para localizar.', 'aviso'); return; }
            avisar('Localizando o endereço no mapa…');
            const base = 'https://nominatim.openstreetmap.org/search?format=json&limit=1&countrycodes=br';
            const p = (k, v) => v ? `&${k}=${encodeURIComponent(v.trim())}` : '';
            const tentativas = [
                base + p('street', `${numero.value} ${rua.value}`) + p('city', cidade.value) + p('state', uf.value) + p('postalcode', cep.value),
                base + p('street', rua.value) + p('city', cidade.value) + p('state', uf.value),
                base + p('postalcode', cep.value) + p('city', cidade.value)
            ];
            for (const url of tentativas) {
                try {
                    const r = await fetch(url, {headers: {'Accept-Language': 'pt-BR'}});
                    const lista = r.ok ? await r.json() : [];
                    if (lista.length) {
                        definir({lat: lista[0].lat, lng: lista[0].lon}, 'Endereço localizado. Se o ponto não estiver certo, arraste o marcador.');
                        return;
                    }
                } catch (e) { /* tenta a próxima forma */ }
            }
            avisar('Não encontramos o endereço automaticamente. Clique no mapa para marcar o local.', 'aviso');
        }

        // CEP completo: preenche rua, bairro, cidade e UF pelo ViaCEP
        let ultimoCep = (cep.value || '').replace(/\D/g, '');
        cep.addEventListener('input', async () => {
            const digitos = cep.value.replace(/\D/g, '').slice(0, 8);
            cep.value = digitos.length > 5 ? digitos.slice(0, 5) + '-' + digitos.slice(5) : digitos;
            if (digitos.length !== 8 || digitos === ultimoCep) return;
            ultimoCep = digitos;
            avisar('Buscando o CEP…');
            try {
                const r = await fetch(`https://viacep.com.br/ws/${digitos}/json/`);
                const d = await r.json();
                if (d.erro) { avisar('CEP não encontrado. Confira os números ou preencha o endereço.', 'aviso'); return; }
                rua.value = d.logradouro || rua.value;
                bairro.value = d.bairro || bairro.value;
                cidade.value = d.localidade || cidade.value;
                uf.value = d.uf || uf.value;
                limparPonto();
                avisar('Endereço preenchido pelo CEP. Informe o número.', 'ok');
                if (!numero.value) numero.focus(); else buscarCoordenadas();
            } catch (e) {
                avisar('Não foi possível consultar o CEP agora. Preencha o endereço manualmente.', 'aviso');
            }
        });

        function limparPonto() { lat.value = ''; lng.value = ''; desenhar(); }
        [rua, numero, bairro, cidade, uf].forEach(input => input.addEventListener('change', () => { limparPonto(); buscarCoordenadas(); }));
        if (raio) raio.addEventListener('input', () => desenhar());

        caixa.querySelector('[data-geo-buscar]')?.addEventListener('click', buscarCoordenadas);
        caixa.querySelector('[data-geo-gps]')?.addEventListener('click', () => {
            if (!navigator.geolocation) { avisar('Seu navegador não permite usar a localização.', 'aviso'); return; }
            avisar('Obtendo sua localização…');
            navigator.geolocation.getCurrentPosition(
                pos => definir({lat: pos.coords.latitude, lng: pos.coords.longitude}, 'Localização atual marcada. Confira o endereço acima.'),
                () => avisar('Não foi possível obter sua localização. Use o CEP ou clique no mapa.', 'aviso'),
                {enableHighAccuracy: true, timeout: 10000});
        });

        // Envio sem ponto no mapa: tenta localizar antes (o servidor também tenta, se faltar)
        caixa.closest('form')?.addEventListener('submit', event => {
            if (temPonto() || caixa.closest('[hidden]') || cep.disabled) return;
            avisar('Marque a localização no mapa antes de salvar.', 'aviso');
        });

        if (elMapa) carregarLeaflet().then(() => {
            mapa = novoMapa(elMapa);
            mapa.setView(temPonto() ? [Number(lat.value), Number(lng.value)] : CENTRO_PADRAO, temPonto() ? 15 : 11);
            mapa.on('click', e => definir(e.latlng, 'Ponto marcado no mapa.'));
            desenhar(true);
            if (temPonto()) avisar('Localização salva. Arraste o marcador se precisar ajustar.', 'ok');
        }).catch(() => avisar('O mapa não carregou. O endereço será localizado ao salvar.', 'aviso'));
    }

    // ---------- Mapa da área de atendimento no perfil ----------
    function iniciarMapaPerfil(el) {
        if (el.dataset.geoPronto) return;
        el.dataset.geoPronto = '1';
        carregarLeaflet().then(() => {
            const centro = [Number(el.dataset.lat), Number(el.dataset.lng)];
            const raioKm = Number(el.dataset.raio || 15);
            const mapa = novoMapa(el);
            // Limites calculados pelo centro: circle.getBounds() falha antes do mapa ter uma visão definida.
            let limites = L.latLng(centro).toBounds(raioKm * 2000);
            mapa.fitBounds(limites);
            L.circle(centro, {radius: raioKm * 1000, color: '#EA580C', weight: 2, fillOpacity: .1}).addTo(mapa);
            L.marker(centro, {icon: pino(), title: 'Região do profissional'}).addTo(mapa).bindPopup('Região aproximada do profissional');
            if (el.dataset.origemLat) {
                const origem = [Number(el.dataset.origemLat), Number(el.dataset.origemLng)];
                L.marker(origem, {icon: casa(), title: 'Seu endereço'}).addTo(mapa).bindPopup('Seu endereço');
                L.polyline([origem, centro], {color: '#0F172A', weight: 2, dashArray: '6 6', opacity: .6}).addTo(mapa);
                limites = limites.extend(origem);
            }
            mapa.fitBounds(limites, {padding: [20, 20]});
        }).catch(() => { el.innerHTML = '<p class="empty-inline">Não foi possível carregar o mapa agora.</p>'; });
    }

    // ---------- Mapa da lista de profissionais ----------
    function iniciarMapaLista(el) {
        if (el.dataset.geoPronto) return;
        el.dataset.geoPronto = '1';
        const dados = window.__mapaLista || {};
        carregarLeaflet().then(() => {
            const mapa = novoMapa(el);
            const pontos = [];
            (dados.pontos || []).forEach(p => {
                // Popup montado com textContent (nome e categoria vêm do banco: nada de HTML vindo do usuário)
                const popup = document.createElement('div');
                popup.className = 'map-popup';
                const link = document.createElement('a');
                link.href = '/prestadores/' + encodeURIComponent(p.id);
                link.textContent = p.nome;
                const categoria = document.createElement('small');
                categoria.textContent = p.categoria || '';
                popup.append(link, categoria);
                if (p.distanciaKm != null) {
                    const distancia = document.createElement('small');
                    distancia.textContent = `${p.distanciaKm.toLocaleString('pt-BR')} km de você${p.atende ? ' · atende sua região' : ''}`;
                    popup.append(distancia);
                }
                L.marker([p.lat, p.lng], {icon: pino(p.atende ? 'is-atende' : ''), title: p.nome}).addTo(mapa).bindPopup(popup);
                pontos.push({ponto: [p.lat, p.lng], atende: p.atende});
            });
            if (dados.origem) {
                L.marker(dados.origem, {icon: casa(), title: 'Seu endereço'}).addTo(mapa).bindPopup('Seu endereço');
            }
            // Com endereço do cliente, enquadra quem atende a região (um profissional distante não "afasta" o mapa).
            const atendem = pontos.filter(p => p.atende).map(p => p.ponto);
            const foco = (dados.origem && atendem.length ? atendem : pontos.map(p => p.ponto)).concat(dados.origem ? [dados.origem] : []);
            if (foco.length) mapa.fitBounds(foco, {padding: [30, 30], maxZoom: 14});
            else mapa.setView(CENTRO_PADRAO, 11);
        }).catch(() => { el.innerHTML = '<p class="empty-inline">Não foi possível carregar o mapa agora.</p>'; });
    }

    function iniciar(raiz = document) {
        raiz.querySelectorAll('[data-address-picker]').forEach(iniciarSeletor);
        raiz.querySelectorAll('[data-mapa-perfil]').forEach(iniciarMapaPerfil);
        raiz.querySelectorAll('[data-mapa-lista]').forEach(iniciarMapaLista);
    }
    window.ObraLinkMapas = {iniciar};
    if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', () => iniciar());
    else iniciar();
})();
