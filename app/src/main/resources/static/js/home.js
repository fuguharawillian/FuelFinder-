import { initializeShell } from "./shell.js";
import { api } from "./api.js";
import { initMap, renderStationMarkers, setUserLocation } from "./map.js";
import { clearElement, makeElement, showMessage } from "./ui.js";

const status = document.getElementById("page-status");
const list = document.getElementById("station-list");
const stationCount = document.getElementById("station-count");
let searchSequence = 0;

document.addEventListener("DOMContentLoaded", async () => {
  const user = await initializeShell({ requiredAuth: true });
  if (!user) return;
  initMap("map");
  document.getElementById("location-query").addEventListener("input", formatPostalCode);
  document.getElementById("search-form").addEventListener("submit", searchFromForm);
  document.getElementById("gps-button").addEventListener("click", useGps);
  document.getElementById("radius").value = "5";
  useGps();
});

function formatPostalCode(event) {
  const input = event.currentTarget;
  const value = input.value;
  if (!/^[\d-]*$/.test(value)) return;

  const digits = value.replace(/\D/g, "").slice(0, 8);
  input.value = digits.length > 5
    ? `${digits.slice(0, 5)}-${digits.slice(5)}`
    : digits;
}

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
  const requestSequence = ++searchSequence;
  if (!navigator.geolocation) {
    showLocationError(
      "Este navegador não oferece localização. Busque por cidade, bairro ou CEP.",
      requestSequence,
    );
    return;
  }
  showMessage(status, "Solicitando sua localização atual…");
  setPendingResults("Aguardando sua localização…");
  navigator.geolocation.getCurrentPosition(async ({ coords }) => {
    if (requestSequence !== searchSequence) return;
    const radiusKm = Number(document.getElementById("radius").value);
    setUserLocation(coords.latitude, coords.longitude);
    await loadStations(`/stations?${new URLSearchParams({
      latitude: String(coords.latitude),
      longitude: String(coords.longitude),
      radiusKm: String(radiusKm),
    })}`, requestSequence, false);
  }, (error) => {
    const message = error.code === 1
      ? "A permissão de localização foi negada. Busque por cidade, bairro ou CEP."
      : "Sua localização não está disponível. Busque por cidade, bairro ou CEP.";
    showLocationError(message, requestSequence);
  }, { enableHighAccuracy: true, timeout: 10000, maximumAge: 60000 });
}

async function loadStations(endpoint, requestSequence = ++searchSequence, fitToStations = true) {
  showMessage(status, "Buscando postos…");
  setPendingResults("Buscando postos…");
  try {
    const stations = await api.get(endpoint);
    if (requestSequence !== searchSequence) return;
    renderStationMarkers(stations, fitToStations);
    renderStationList(stations);
    stationCount.textContent = String(stations.length);
    showMessage(status, `${stations.length} posto(s) encontrado(s).`, "success");
  } catch (error) {
    if (requestSequence !== searchSequence) return;
    showMessage(status, error.message, "error");
    clearElement(list);
    renderStationMarkers([]);
    stationCount.textContent = "—";
    list.append(makeElement("p", "Não foi possível carregar os postos. Tente novamente.", "muted"));
  }
}

function setPendingResults(message) {
  clearElement(list);
  list.append(makeElement("p", message, "muted"));
  renderStationMarkers([]);
  stationCount.textContent = "—";
}

function showLocationError(message, requestSequence) {
  if (requestSequence !== searchSequence) return;
  showMessage(status, message, "error");
  setPendingResults("Use a busca por cidade, bairro ou CEP para encontrar postos.");
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
    const link = makeElement("a", "Ver preços, avaliações e rotas", "btn btn-outline-primary");
    link.classList.add("mt-2");
    link.href = `/station-detail.html?id=${encodeURIComponent(station.id)}`;
    card.append(link);
    list.append(card);
  });
}
