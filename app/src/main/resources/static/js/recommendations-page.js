import { initializeShell } from "./shell.js";
import { api } from "./api.js";
import { makeElement, formatCurrency, showMessage } from "./ui.js";

const status = document.getElementById("page-status");
let vehicles = [];

document.addEventListener("DOMContentLoaded", async () => {
  const user = await initializeShell({ requiredRole: "ROLE_DRIVER" });
  if (!user) return;
  document.getElementById("recommendation-form").addEventListener("submit", requestRecommendation);
  try {
    vehicles = await api.get("/vehicles");
    const select = document.getElementById("vehicle-id");
    vehicles.forEach((vehicle) => {
      const option = makeElement("option", `${vehicle.nickname} — ${vehicle.brand} ${vehicle.model}`);
      option.value = vehicle.id;
      select.append(option);
    });
    if (!vehicles.length) {
      showMessage(status, "Cadastre um veículo antes de solicitar uma recomendação.", "error");
      document.getElementById("recommendation-form").classList.add("hidden");
    }
  } catch (error) {
    showMessage(status, error.message, "error");
  }
});

async function requestRecommendation(event) {
  event.preventDefault();
  const form = new FormData(event.currentTarget);
  const latitude = Number(form.get("latitude"));
  const longitude = Number(form.get("longitude"));
  const vehicleId = form.get("vehicleId");
  if (!Number.isFinite(latitude) || latitude < -90 || latitude > 90
      || !Number.isFinite(longitude) || longitude < -180 || longitude > 180) {
    showMessage(status, "Informe coordenadas válidas.", "error");
    return;
  }
  showMessage(status, "Calculando recomendações…");
  try {
    const query = new URLSearchParams({
      vehicleId,
      latitude: String(latitude),
      longitude: String(longitude),
      radiusKm: String(Number(form.get("radiusKm"))),
    });
    const result = await api.get(`/recommendations/fuel?${query}`);
    renderRecommendation(result);
    showMessage(status, "Recomendação calculada com os preços históricos disponíveis.", "success");
  } catch (error) {
    showMessage(status, error.message, "error");
  }
}

function renderRecommendation(result) {
  const host = document.getElementById("recommendation-result");
  host.replaceChildren();
  const vehicle = vehicles.find((entry) => entry.nickname === result.vehicle?.nickname);
  const heading = makeElement("section", undefined, "panel stack");
  heading.append(makeElement("h2", `Combustível recomendado: ${result.recommendedFuel || "—"}`));
  heading.append(makeElement("p", result.explanation || "Sem explicação disponível."));
  if (result.parityPercentage !== null && result.parityPercentage !== undefined) {
    heading.append(makeElement("p", `Paridade: ${Number(result.parityPercentage).toFixed(1)}%`, "pill"));
  }
  if (vehicle) heading.append(makeElement("p", `Veículo: ${vehicle.nickname}`, "muted"));
  host.append(heading);

  const options = makeElement("section", undefined, "panel");
  options.append(makeElement("h2", "Melhores opções próximas"));
  const list = makeElement("div", undefined, "stack");
  (result.topOptions || []).forEach((option) => {
    const row = makeElement("article", undefined, "admin-row");
    const details = makeElement("div");
    details.append(makeElement("strong", option.stationName));
    details.append(makeElement("div", `${option.fuelType} · ${Number(option.distanceKm).toFixed(1)} km · ${option.brand || "Bandeira não informada"}`, "muted"));
    details.append(makeElement("div", `Tanque: ${formatCurrency(option.estimatedFullTankCost)} · Ida e volta: ${formatCurrency(option.estimatedRoundTripCost)}`, "muted"));
    row.append(details, makeElement("span", formatCurrency(option.price, option.unitOfMeasure?.replace("R$/", "") || ""), "price"));
    const link = makeElement("a", "Ver posto", "button secondary");
    link.href = `/station-detail.html?id=${encodeURIComponent(option.stationId)}`;
    row.append(link);
    list.append(row);
  });
  if (!result.topOptions?.length) list.append(makeElement("p", "Não há opções que correspondam ao veículo nessa região.", "muted"));
  options.append(list);
  host.append(options);
}
