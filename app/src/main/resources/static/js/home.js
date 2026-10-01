import { initializeShell } from "./shell.js";
import { api } from "./api.js";
import { initMap, renderStationMarkers, setUserLocation } from "./map.js";
import { clearElement, makeElement, showMessage } from "./ui.js";

const status = document.getElementById("page-status");
const list = document.getElementById("station-list");

document.addEventListener("DOMContentLoaded", async () => {
  await initializeShell();
  initMap("map");
  document.getElementById("search-form").addEventListener("submit", searchFromForm);
  document.getElementById("gps-button").addEventListener("click", useGps);
});

async function searchFromForm(event) {
  event.preventDefault();
  const query = document.getElementById("location-query").value.trim();
  const radiusKm = Number(document.getElementById("radius").value);
  if (!query) {
    showMessage(status, "Informe cidade, bairro ou CEP, ou use sua localização.", "error");
    return;
  }
  const params = new URLSearchParams({ query, radiusKm: String(radiusKm) });
  await loadStations(`/stations?${params}`);
}

async function useGps() {
  if (!navigator.geolocation) {
    showMessage(status, "Este navegador não oferece localização. Use a busca por cidade, bairro ou CEP.", "error");
    return;
  }
  showMessage(status, "Solicitando sua localização…");
  navigator.geolocation.getCurrentPosition(async ({ coords }) => {
    const radiusKm = Number(document.getElementById("radius").value);
    setUserLocation(coords.latitude, coords.longitude);
    await loadStations(`/stations?${new URLSearchParams({
      latitude: String(coords.latitude),
      longitude: String(coords.longitude),
      radiusKm: String(radiusKm),
    })}`);
  }, () => {
    showMessage(status, "Não foi possível obter sua localização. Permita o acesso ao GPS ou faça uma busca textual.", "error");
    document.getElementById("location-query").focus();
  }, { enableHighAccuracy: true, timeout: 10000, maximumAge: 60000 });
}

async function loadStations(endpoint) {
  showMessage(status, "Buscando postos…");
  try {
    const stations = await api.get(endpoint);
    renderStationMarkers(stations);
    renderStationList(stations);
    showMessage(status, `${stations.length} posto(s) encontrado(s).`, "success");
  } catch (error) {
    showMessage(status, error.message, "error");
    clearElement(list);
  }
}

function renderStationList(stations) {
  clearElement(list);
  if (!stations.length) {
    list.append(makeElement("p", "Nenhum posto encontrado nessa busca.", "muted"));
    return;
  }
  stations.forEach((station) => {
    const card = makeElement("article", undefined, "station-card");
    const title = makeElement("h3", station.tradeName || station.corporateName);
    card.append(title);
    card.append(makeElement("div", station.brand || "Bandeira não informada", "muted"));
    card.append(makeElement("div", [station.address, station.city, station.state].filter(Boolean).join(" · ")));
    const metadata = makeElement("div", undefined, "station-meta");
    if (station.distanceKm !== null && station.distanceKm !== undefined) {
      metadata.append(makeElement("span", `${Number(station.distanceKm).toFixed(1)} km`));
    }
    metadata.append(makeElement("span", `${Number(station.averageRating || 0).toFixed(1)} ★ · ${station.totalReviews || 0} avaliações`));
    card.append(metadata);
    const link = makeElement("a", "Ver preços, avaliações e rotas", "button secondary");
    link.href = `/station-detail.html?id=${encodeURIComponent(station.id)}`;
    card.append(link);
    list.append(card);
  });
}
