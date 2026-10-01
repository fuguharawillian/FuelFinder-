import { initializeShell } from "./shell.js";
import { api } from "./api.js";
import { clearElement, makeElement, showMessage } from "./ui.js";

const status = document.getElementById("page-status");
const list = document.getElementById("vehicle-list");
const fuelSelect = document.getElementById("fuel-type");

document.addEventListener("DOMContentLoaded", async () => {
  const user = await initializeShell({ requiredRole: "ROLE_DRIVER" });
  if (!user) return;
  fuelSelect.addEventListener("change", updateConsumptionFields);
  updateConsumptionFields();
  document.getElementById("vehicle-form").addEventListener("submit", createVehicle);
  await loadVehicles();
});

function updateConsumptionFields() {
  const fuel = fuelSelect.value;
  const fields = document.getElementById("consumption-fields");
  fields.replaceChildren();
  const options = fuel === "FLEX"
    ? [["averageConsumptionGasoline", "Consumo gasolina (km/L)"], ["averageConsumptionEthanol", "Consumo etanol (km/L)"]]
    : fuel === "GASOLINE" ? [["averageConsumptionGasoline", "Consumo gasolina (km/L)"]]
      : fuel === "ETHANOL" ? [["averageConsumptionEthanol", "Consumo etanol (km/L)"]]
        : fuel === "DIESEL" ? [["averageConsumptionDiesel", "Consumo diesel (km/L)"]]
          : [["averageConsumptionCng", "Consumo GNV (km/m³)"]];
  options.forEach(([name, labelText]) => {
    const label = makeElement("label", labelText);
    const input = document.createElement("input");
    input.name = name;
    input.type = "number";
    input.min = "1";
    input.max = "40";
    input.step = "0.01";
    input.required = true;
    input.setAttribute("aria-label", labelText);
    label.append(input);
    fields.append(label);
  });
  document.getElementById("capacity-unit").textContent = fuel === "CNG" ? "m³" : "L";
}

async function createVehicle(event) {
  event.preventDefault();
  const form = event.currentTarget;
  const data = new FormData(form);
  const fuel = data.get("fuelTypeAccepted");
  const capacityUnit = fuel === "CNG" ? "CUBIC_METER" : "LITER";
  const consumptionUnit = fuel === "CNG" ? "KM_PER_CUBIC_METER" : "KM_PER_LITER";
  const body = {
    nickname: data.get("nickname"),
    brand: data.get("brand"),
    model: data.get("model"),
    yearManufacture: Number(data.get("yearManufacture")),
    fuelTypeAccepted: fuel,
    tankCapacity: { value: Number(data.get("tankCapacity")), unit: capacityUnit },
  };
  for (const input of document.querySelectorAll("#consumption-fields input")) {
    body[input.name] = { value: Number(data.get(input.name)), unit: consumptionUnit };
  }
  showMessage(status, "Salvando veículo…");
  try {
    await api.post("/vehicles", body);
    form.reset();
    updateConsumptionFields();
    showMessage(status, "Veículo cadastrado.", "success");
    await loadVehicles();
  } catch (error) {
    showMessage(status, error.message, "error");
  }
}

async function loadVehicles() {
  try {
    const vehicles = await api.get("/vehicles");
    renderVehicles(vehicles);
  } catch (error) {
    showMessage(status, error.message, "error");
  }
}

function renderVehicles(vehicles) {
  clearElement(list);
  if (!vehicles.length) {
    list.append(makeElement("p", "Você ainda não cadastrou veículos.", "muted"));
    return;
  }
  vehicles.forEach((vehicle) => {
    const card = makeElement("article", undefined, "vehicle-row");
    const detail = makeElement("div");
    detail.append(makeElement("h3", vehicle.nickname));
    detail.append(makeElement("p", `${vehicle.brand} ${vehicle.model} · ${vehicle.yearManufacture} · ${fuelLabel(vehicle.fuelTypeAccepted)}`, "muted"));
    const tankUnit = vehicle.tankCapacity?.unit === "CUBIC_METER" ? "m³" : "L";
    detail.append(makeElement("p", `Tanque: ${vehicle.tankCapacity?.value} ${tankUnit}`, "muted"));
    const actions = makeElement("div", undefined, "form-actions");
    const edit = makeElement("button", "Editar apelido", "secondary");
    edit.type = "button";
    edit.addEventListener("click", () => editNickname(vehicle));
    const remove = makeElement("button", "Excluir", "danger");
    remove.type = "button";
    remove.addEventListener("click", () => deleteVehicle(vehicle.id));
    actions.append(edit, remove);
    card.append(detail, actions);
    list.append(card);
  });
}

async function editNickname(vehicle) {
  const nickname = window.prompt("Novo apelido do veículo:", vehicle.nickname);
  if (nickname === null || !nickname.trim()) return;
  try {
    await api.patch(`/vehicles/${vehicle.id}`, { nickname: nickname.trim() });
    showMessage(status, "Apelido atualizado.", "success");
    await loadVehicles();
  } catch (error) {
    showMessage(status, error.message, "error");
  }
}

async function deleteVehicle(id) {
  if (!window.confirm("Deseja excluir este veículo?")) return;
  try {
    await api.delete(`/vehicles/${id}`);
    showMessage(status, "Veículo excluído.", "success");
    await loadVehicles();
  } catch (error) {
    showMessage(status, error.message, "error");
  }
}

function fuelLabel(type) {
  return ({ GASOLINE: "Gasolina", ETHANOL: "Etanol", FLEX: "Flex", DIESEL: "Diesel", CNG: "GNV" })[type] || type;
}
