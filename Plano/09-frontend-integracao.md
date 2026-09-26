# Fase 9 — Frontend Web Responsivo e Integração

## Objetivo

Construir a interface web responsiva do FuelFinder usando HTML5 semântico, Tailwind CSS 3.4+, Vanilla JavaScript ES2023+ e Leaflet 1.9.4 para visualização cartográfica, consumindo a API REST do backend com autenticação JWT.

**Branch:** `feature/frontend-leaflet`
**Dependências:** Fases 1-7 (todas as fases de backend)

---

## Estrutura de Arquivos

```text
src/main/resources/static/
├── index.html                  # Página principal com mapa e busca
├── login.html                  # Login e registro de motoristas
├── vehicles.html               # Gestão de veículos do motorista
├── station-detail.html         # Detalhes do posto (preços, avaliações)
├── recommendations.html        # Tela de recomendação personalizada
├── admin/                      # Painel administrativo
│   ├── index.html              # Dashboard admin
│   ├── stations.html           # Gestão de postos
│   ├── prices.html             # Gestão de preços
│   ├── reviews.html            # Moderação de avaliações
│   └── anp-import.html         # Importação ANP
├── css/
│   └── styles.css              # Customizações sobre Tailwind
├── js/
│   ├── api.js                  # Client HTTP com interceptor JWT
│   ├── auth.js                 # Login, registro, logout
│   ├── map.js                  # Inicialização e controle do Leaflet
│   ├── stations.js             # Busca e listagem de postos
│   ├── vehicles.js             # CRUD de veículos
│   ├── reviews.js              # Avaliações
│   ├── recommendations.js      # Recomendações
│   └── admin.js                # Funções administrativas
└── img/
    └── markers/                # Ícones de marcadores do mapa
        ├── station-default.png
        ├── station-selected.png
        └── user-location.png
```

---

## Tarefas de Implementação

### 9.1 Client HTTP com Interceptor JWT (`api.js`)

```javascript
// js/api.js — Módulo de comunicação com a API

const API_BASE_URL = '/';

/**
 * Fetch wrapper que adiciona token JWT automaticamente
 */
async function apiRequest(endpoint, options = {}) {
    const token = localStorage.getItem('accessToken');

    const headers = {
        'Content-Type': 'application/json',
        ...options.headers,
    };

    if (token) {
        headers['Authorization'] = `Bearer ${token}`;
    }

    const response = await fetch(`${API_BASE_URL}${endpoint}`, {
        ...options,
        headers,
    });

    // Token expirado ou inválido
    if (response.status === 401) {
        localStorage.removeItem('accessToken');
        localStorage.removeItem('user');
        window.location.href = '/login.html';
        return;
    }

    if (!response.ok) {
        const error = await response.json().catch(() => ({}));
        throw new Error(error.detail || `Erro ${response.status}`);
    }

    if (response.status === 204) return null;
    return response.json();
}

// Helpers
const api = {
    get: (url) => apiRequest(url),
    post: (url, body) => apiRequest(url, {
        method: 'POST', body: JSON.stringify(body)
    }),
    patch: (url, body) => apiRequest(url, {
        method: 'PATCH', body: JSON.stringify(body)
    }),
    delete: (url) => apiRequest(url, { method: 'DELETE' }),
};
```

### 9.2 Autenticação no Cliente (`auth.js`)

```javascript
// js/auth.js

async function register(fullName, email, password) {
    const data = await api.post('auth/register', {
        fullName, email, password
    });
    localStorage.setItem('accessToken', data.accessToken);
    localStorage.setItem('user', JSON.stringify(data.user));
    window.location.href = '/index.html';
}

async function login(email, password) {
    const data = await api.post('auth/login', { email, password });
    localStorage.setItem('accessToken', data.accessToken);
    localStorage.setItem('user', JSON.stringify(data.user));
    window.location.href = '/index.html';
}

function logout() {
    api.post('auth/logout').catch(() => {});
    localStorage.removeItem('accessToken');
    localStorage.removeItem('user');
    window.location.href = '/login.html';
}

function getCurrentUser() {
    const user = localStorage.getItem('user');
    return user ? JSON.parse(user) : null;
}

function isAuthenticated() {
    return !!localStorage.getItem('accessToken');
}

function isAdmin() {
    const user = getCurrentUser();
    return user && user.role === 'ROLE_ADMIN';
}
```

### 9.3 Mapa Leaflet com OpenStreetMap (`map.js`)

```javascript
// js/map.js

let map;
let markers = [];
let userMarker;

/**
 * Inicializa o mapa Leaflet com tiles do OpenStreetMap
 */
function initMap(containerId, lat = -23.5505, lng = -46.6333, zoom = 13) {
    map = L.map(containerId).setView([lat, lng], zoom);

    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
        attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>',
        maxZoom: 19,
    }).addTo(map);

    return map;
}

/**
 * Adiciona marcador do usuário no mapa
 */
function setUserLocation(lat, lng) {
    if (userMarker) {
        map.removeLayer(userMarker);
    }
    userMarker = L.marker([lat, lng], {
        icon: L.icon({
            iconUrl: '/img/markers/user-location.png',
            iconSize: [32, 32],
            iconAnchor: [16, 32],
        })
    }).addTo(map).bindPopup('Sua localização');

    map.setView([lat, lng], 14);
}

/**
 * Renderiza marcadores dos postos no mapa
 */
function renderStationMarkers(stations) {
    // Limpar marcadores anteriores
    markers.forEach(m => map.removeLayer(m));
    markers = [];

    stations.forEach(station => {
        const marker = L.marker([station.latitude, station.longitude])
            .addTo(map)
            .bindPopup(createPopupContent(station));

        marker.on('click', () => showStationDetail(station));
        markers.push(marker);
    });

    // Ajustar zoom para mostrar todos os marcadores
    if (markers.length > 0) {
        const group = L.featureGroup(markers);
        map.fitBounds(group.getBounds().pad(0.1));
    }
}

/**
 * Cria conteúdo do popup do marcador
 */
function createPopupContent(station) {
    const prices = station.prices
        ? station.prices.map(p =>
            `<div>${p.fuelTypeName}: <strong>R$ ${p.saleValue.toFixed(2)}</strong></div>`
          ).join('')
        : '<div>Sem preços disponíveis</div>';

    return `
        <div class="p-2">
            <h3 class="font-bold text-sm">${station.tradeName || station.corporateName}</h3>
            <p class="text-xs text-gray-600">${station.brand || 'Bandeira Branca'}</p>
            <p class="text-xs">${station.distanceKm?.toFixed(2) || '?'} km</p>
            <div class="mt-1 text-xs">${prices}</div>
            <div class="mt-2 flex gap-1">
                <button onclick="openGoogleMaps(${station.latitude}, ${station.longitude})"
                        class="text-xs bg-blue-500 text-white px-2 py-1 rounded"
                        aria-label="Abrir rota no Google Maps">
                    Google Maps
                </button>
                <button onclick="openWaze(${station.latitude}, ${station.longitude})"
                        class="text-xs bg-purple-500 text-white px-2 py-1 rounded"
                        aria-label="Abrir rota no Waze">
                    Waze
                </button>
            </div>
        </div>
    `;
}
```

### 9.4 Geolocation API com Fallback (`stations.js`)

```javascript
// js/stations.js

/**
 * Obtém localização do usuário via GPS ou fallback textual
 */
async function getUserLocation() {
    return new Promise((resolve, reject) => {
        if ('geolocation' in navigator) {
            navigator.geolocation.getCurrentPosition(
                (position) => {
                    resolve({
                        latitude: position.coords.latitude,
                        longitude: position.coords.longitude,
                    });
                },
                (error) => {
                    console.warn('GPS negado, usando fallback:', error.message);
                    reject(error);
                },
                { enableHighAccuracy: true, timeout: 10000 }
            );
        } else {
            reject(new Error('Geolocation API não disponível'));
        }
    });
}

/**
 * Busca postos próximos pela API
 */
async function searchNearbyStations(lat, lng, radiusKm = 5) {
    const stations = await api.get(
        `stations?latitude=${lat}&longitude=${lng}&radiusKm=${radiusKm}`
    );
    renderStationMarkers(stations);
    renderStationList(stations);
    return stations;
}

/**
 * Renderiza lista ordenada de postos
 */
function renderStationList(stations) {
    const container = document.getElementById('station-list');
    container.innerHTML = '';

    stations.forEach(station => {
        const card = document.createElement('article');
        card.className = 'p-4 border rounded-lg shadow-sm hover:shadow-md transition';
        card.setAttribute('aria-label', `Posto ${station.tradeName}`);
        card.innerHTML = `
            <div class="flex justify-between items-start">
                <div>
                    <h3 class="font-semibold text-lg">${station.tradeName || station.corporateName}</h3>
                    <p class="text-sm text-gray-600">${station.brand || 'Bandeira Branca'}</p>
                    <p class="text-sm text-gray-500">${station.address}</p>
                </div>
                <div class="text-right">
                    <span class="text-sm font-medium">${station.distanceKm?.toFixed(1)} km</span>
                    <div class="flex items-center gap-1 mt-1">
                        <span class="text-yellow-500">★</span>
                        <span class="text-sm">${station.averageRating?.toFixed(1) || '—'}</span>
                        <span class="text-xs text-gray-400">(${station.totalReviews || 0})</span>
                    </div>
                </div>
            </div>
            <div class="mt-2 flex gap-2">
                <a href="/station-detail.html?id=${station.id}"
                   class="text-sm text-blue-600 hover:underline">Ver detalhes</a>
                <button onclick="openGoogleMaps(${station.latitude}, ${station.longitude})"
                        class="text-sm text-green-600 hover:underline"
                        aria-label="Abrir rota para ${station.tradeName}">
                    Rotas
                </button>
            </div>
        `;
        container.appendChild(card);
    });
}
```

### 9.5 Deep Links de Navegação (Rotas)

```javascript
/**
 * Abre rota no Google Maps via deep link
 */
function openGoogleMaps(lat, lng) {
    const url = `https://www.google.com/maps/dir/?api=1&destination=${lat},${lng}`;
    window.open(url, '_blank');
}

/**
 * Abre rota no Waze via deep link
 */
function openWaze(lat, lng) {
    const url = `https://waze.com/ul?ll=${lat},${lng}&navigate=yes`;
    window.open(url, '_blank');
}
```

### 9.6 Página Principal (`index.html`)

```html
<!DOCTYPE html>
<html lang="pt-BR">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>FuelFinder — Encontre o Melhor Preço de Combustível</title>

    <!-- Tailwind CSS via CDN -->
    <script src="https://cdn.tailwindcss.com"></script>

    <!-- Leaflet CSS -->
    <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />

    <link rel="stylesheet" href="/css/styles.css" />
</head>
<body class="bg-gray-50 min-h-screen">

    <!-- Navegação -->
    <nav class="bg-white shadow-sm" aria-label="Navegação principal">
        <div class="max-w-7xl mx-auto px-4 py-3 flex justify-between items-center">
            <a href="/" class="text-xl font-bold text-green-600">⛽ FuelFinder</a>
            <div id="nav-actions" class="flex gap-4 items-center">
                <!-- Preenchido via JS -->
            </div>
        </div>
    </nav>

    <!-- Conteúdo principal -->
    <main class="max-w-7xl mx-auto px-4 py-6">
        <!-- Barra de busca -->
        <section class="mb-6" aria-label="Busca de postos">
            <div class="flex gap-2">
                <input type="text" id="search-input"
                       placeholder="Buscar por cidade, bairro ou CEP..."
                       class="flex-1 px-4 py-2 border rounded-lg focus:ring-2 focus:ring-green-500"
                       aria-label="Campo de busca de postos" />
                <button id="btn-search"
                        class="px-6 py-2 bg-green-600 text-white rounded-lg hover:bg-green-700"
                        aria-label="Buscar postos">
                    Buscar
                </button>
                <button id="btn-gps"
                        class="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700"
                        aria-label="Usar localização GPS">
                    📍 GPS
                </button>
            </div>
        </section>

        <!-- Mapa e Lista lado a lado -->
        <div class="grid grid-cols-1 lg:grid-cols-2 gap-6">
            <!-- Mapa Leaflet -->
            <section aria-label="Mapa de postos">
                <div id="map" class="h-96 lg:h-[600px] rounded-lg shadow-md"></div>
            </section>

            <!-- Lista de postos -->
            <section aria-label="Lista de postos encontrados">
                <h2 class="text-lg font-semibold mb-4">Postos Encontrados</h2>
                <div id="station-list" class="space-y-4 max-h-[600px] overflow-y-auto">
                    <p class="text-gray-400 text-center py-8">
                        Use o GPS ou busque por localização para encontrar postos próximos.
                    </p>
                </div>
            </section>
        </div>
    </main>

    <!-- Leaflet JS -->
    <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>

    <!-- Scripts da aplicação -->
    <script src="/js/api.js"></script>
    <script src="/js/auth.js"></script>
    <script src="/js/map.js"></script>
    <script src="/js/stations.js"></script>

    <script>
        // Inicialização
        document.addEventListener('DOMContentLoaded', () => {
            initMap('map');
            updateNavBar();

            // Botão GPS
            document.getElementById('btn-gps').addEventListener('click', async () => {
                try {
                    const loc = await getUserLocation();
                    setUserLocation(loc.latitude, loc.longitude);
                    await searchNearbyStations(loc.latitude, loc.longitude);
                } catch (e) {
                    alert('Não foi possível obter sua localização. Use a busca textual.');
                }
            });

            // Botão buscar (placeholder para implementação de geocoding no frontend)
            document.getElementById('btn-search').addEventListener('click', () => {
                const query = document.getElementById('search-input').value;
                if (query) {
                    // TODO: implementar busca por texto/CEP
                    alert('Busca textual será implementada com Geoapify.');
                }
            });
        });

        function updateNavBar() {
            const nav = document.getElementById('nav-actions');
            const user = getCurrentUser();

            if (user) {
                nav.innerHTML = `
                    <a href="/vehicles.html" class="text-sm text-gray-600 hover:text-green-600">Meus Veículos</a>
                    <a href="/recommendations.html" class="text-sm text-gray-600 hover:text-green-600">Recomendações</a>
                    ${isAdmin() ? '<a href="/admin/index.html" class="text-sm text-red-600 hover:text-red-800">Admin</a>' : ''}
                    <span class="text-sm text-gray-500">${user.fullName}</span>
                    <button onclick="logout()" class="text-sm text-red-500 hover:underline">Sair</button>
                `;
            } else {
                nav.innerHTML = `
                    <a href="/login.html" class="px-4 py-2 bg-green-600 text-white rounded-lg hover:bg-green-700">Entrar</a>
                `;
            }
        }
    </script>
</body>
</html>
```

---

## Telas do Aplicativo

| Tela | URL | Descrição | Acesso |
|------|-----|-----------|--------|
| Busca de postos (mapa) | `/index.html` | Mapa Leaflet + lista ordenada | Público |
| Login / Registro | `/login.html` | Formulários de autenticação | Público |
| Meus Veículos | `/vehicles.html` | CRUD de veículos | Motorista |
| Detalhes do Posto | `/station-detail.html?id=...` | Preços, avaliações, rotas | Público |
| Recomendação | `/recommendations.html` | Resultado personalizado | Motorista |
| Admin — Dashboard | `/admin/index.html` | Visão geral | Admin |
| Admin — Postos | `/admin/stations.html` | Gestão de postos | Admin |
| Admin — Preços | `/admin/prices.html` | Gestão de preços | Admin |
| Admin — Avaliações | `/admin/reviews.html` | Moderação | Admin |
| Admin — ANP | `/admin/anp-import.html` | Importação ANP | Admin |

---

## Requisitos de Acessibilidade (WCAG/W3C)

1. **HTML semântico:** `<main>`, `<nav>`, `<section>`, `<article>`, `<header>`, `<footer>`
2. **`aria-label`** em todos os botões e elementos interativos
3. **Contraste de cores:** mínimo 4.5:1 para texto normal
4. **Foco visível:** outline em elementos focáveis
5. **Responsividade:** Tailwind `sm:`, `md:`, `lg:` breakpoints
6. **Mobile-first:** layout em coluna única no mobile, grid no desktop

---

## Responsividade

| Viewport | Layout |
|----------|--------|
| **Mobile** (< 768px) | Mapa empilhado sobre lista, menu hamburger |
| **Tablet** (768-1024px) | Mapa e lista lado a lado, 50/50 |
| **Desktop** (> 1024px) | Mapa e lista lado a lado, mapa maior |

---

## Testes Manuais

### Funcional
- [ ] Login e registro funcionam
- [ ] Mapa renderiza com tiles do OpenStreetMap
- [ ] GPS solicita permissão e centraliza o mapa
- [ ] Busca retorna postos com marcadores no mapa
- [ ] Clique no marcador mostra popup com preços
- [ ] Botão "Rotas" abre Google Maps / Waze
- [ ] CRUD de veículos funciona
- [ ] Avaliação de posto funciona (criar, editar)
- [ ] Recomendação exibe resultado personalizado
- [ ] Painel admin acessível apenas para ROLE_ADMIN

### Responsividade
- [ ] Funciona em viewport 375px (iPhone SE)
- [ ] Funciona em viewport 768px (iPad)
- [ ] Funciona em viewport 1440px (Desktop)

### Acessibilidade
- [ ] Todos os botões têm `aria-label`
- [ ] HTML usa tags semânticas
- [ ] Navegação funciona via teclado (Tab)
- [ ] Contraste adequado em todos os elementos de texto

---

## Critérios de Aceitação

- [ ] Mapa Leaflet 1.9.4 com tiles OpenStreetMap renderiza corretamente
- [ ] Marcadores no mapa correspondem aos postos da API
- [ ] GPS solicita permissão e faz fallback para busca textual
- [ ] Botão "Rotas" abre Google Maps e Waze com coordenadas corretas
- [ ] Interface responsiva em mobile, tablet e desktop
- [ ] Tags semânticas e `aria-label` em elementos interativos
- [ ] JWT armazenado no localStorage e enviado em todas as requests
- [ ] Roteamento de telas funciona (login → mapa → detalhes → etc.)
- [ ] Painel admin protegido por verificação de role no cliente

---

## Commit Sugerido

```
feat: build responsive frontend with Leaflet map, auth flow and admin panel
```
