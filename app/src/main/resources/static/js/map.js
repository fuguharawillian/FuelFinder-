import { formatDate, makeElement } from "./ui.js";
import { api } from "./api.js";

let map;
let stationMarkers = [];
let userMarker;

export function initMap(containerId, latitude = -23.5505, longitude = -46.6333, zoom = 11) {
  if (!window.L) throw new Error("A biblioteca Leaflet não foi carregada.");
  map = window.L.map(containerId, { zoomControl: false }).setView([latitude, longitude], zoom);
  window.L.control.zoom({
    zoomInTitle: "Aumentar zoom",
    zoomOutTitle: "Diminuir zoom",
  }).addTo(map);
  window.L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", {
    attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>',
    maxZoom: 19,
  }).addTo(map);
  return map;
}

export function setUserLocation(latitude, longitude) {
  if (!map) return;
  if (userMarker) map.removeLayer(userMarker);
  userMarker = window.L.circleMarker([latitude, longitude], {
    radius: 8,
    color: "#fff",
    weight: 3,
    fillColor: "#1774d2",
    fillOpacity: 1,
  }).addTo(map).bindPopup("Sua localização");
  map.setView([latitude, longitude], 14);
}

export function renderStationMarkers(stations) {
  if (!map) return;
  stationMarkers.forEach((marker) => map.removeLayer(marker));
  stationMarkers = [];
  stations.forEach((station) => {
    if (!Number.isFinite(Number(station.latitude)) || !Number.isFinite(Number(station.longitude))) return;
    const marker = window.L.marker([station.latitude, station.longitude])
      .addTo(map)
      .bindPopup(createStationPopup(station));
    marker.on("popupopen", async () => {
      try {
        const prices = await api.get(`/stations/${station.id}/fuel-prices`);
        marker.setPopupContent(createStationPopup(station, prices));
      } catch {
        marker.setPopupContent(createStationPopup(station, null));
      }
    });
    stationMarkers.push(marker);
  });
  if (stationMarkers.length === 1) {
    map.setView(stationMarkers[0].getLatLng(), 14);
  } else if (stationMarkers.length > 1) {
    map.fitBounds(window.L.featureGroup(stationMarkers).getBounds().pad(0.12), { maxZoom: 15 });
  }
}

function createStationPopup(station, prices) {
  const content = makeElement("div", undefined, "stack");
  content.append(makeElement("strong", station.tradeName || station.corporateName));
  content.append(makeElement("span", station.brand || "Bandeira não informada", "muted"));
  if (station.distanceKm !== null && station.distanceKm !== undefined) {
    content.append(makeElement("span", `${Number(station.distanceKm).toFixed(1)} km de distância`));
  }
  content.append(makeElement("span", `${Number(station.averageRating || 0).toFixed(1)} ★ · ${station.totalReviews || 0} avaliações`));
  if (prices) {
    if (prices.length) {
      prices.forEach((price) => {
        content.append(makeElement(
          "span",
          `${price.fuelTypeName}: ${new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" }).format(Number(price.saleValue))} (${formatDate(price.collectionDate)})`,
        ));
      });
    } else {
      content.append(makeElement("span", "Sem preços disponíveis."));
    }
  } else {
    content.append(makeElement("span", "Preços indisponíveis no momento.", "muted"));
  }
  const detail = makeElement("a", "Ver preços e detalhes");
  detail.href = `/station-detail.html?id=${encodeURIComponent(station.id)}`;
  content.append(detail);
  const routes = makeElement("div", undefined, "route-actions");
  routes.append(routeButton("Google Maps", `https://www.google.com/maps/dir/?api=1&destination=${station.latitude},${station.longitude}`));
  routes.append(routeButton("Waze", `https://waze.com/ul?ll=${station.latitude},${station.longitude}&navigate=yes`));
  content.append(routes);
  return content;
}

function routeButton(label, url) {
  const link = makeElement("a", label, "btn btn-outline-primary");
  link.href = url;
  link.target = "_blank";
  link.rel = "noopener noreferrer";
  link.setAttribute("aria-label", `Abrir rota no ${label}`);
  return link;
}
