import { initializeShell } from "./shell.js";
import { api } from "./api.js";
import { clearElement, formatCurrency, formatDate, makeElement, showMessage } from "./ui.js";

const page = document.body.dataset.adminPage;
const status = document.getElementById("page-status");

document.addEventListener("DOMContentLoaded", async () => {
  const user = await initializeShell({ requiredRole: "ROLE_ADMIN" });
  if (!user) return;
  if (page === "stations") setupStations();
  if (page === "prices") setupPrices();
  if (page === "reviews") setupReviews();
  if (page === "anp") setupAnp();
});

function setupStations() {
  document.getElementById("station-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const payload = Object.fromEntries(form.entries());
    payload.latitude = Number(payload.latitude);
    payload.longitude = Number(payload.longitude);
    showMessage(status, "Cadastrando posto…");
    try {
      await api.post("/stations", payload);
      event.currentTarget.reset();
      showMessage(status, "Posto cadastrado.", "success");
      await searchStations();
    } catch (error) {
      showMessage(status, error.message, "error");
    }
  });
  document.getElementById("station-search-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    await searchStations();
  });
}

async function searchStations() {
  const query = document.getElementById("station-search").value.trim();
  const endpoint = query ? `/stations?query=${encodeURIComponent(query)}` : "/stations";
  try {
    const stations = await api.get(endpoint);
    const host = document.getElementById("admin-station-list");
    clearElement(host);
    if (!stations.length) host.append(makeElement("p", "Nenhum posto encontrado.", "muted"));
    stations.forEach((station) => {
      const row = makeElement("article", undefined, "admin-row");
      const summary = makeElement("div");
      summary.append(makeElement("strong", station.tradeName || station.corporateName));
      summary.append(makeElement("div", `${station.address || ""} · ${station.city}/${station.state}`, "muted"));
      const deactivate = makeElement("button", "Desativar", "danger");
      deactivate.type = "button";
      deactivate.addEventListener("click", async () => {
        if (!window.confirm("Desativar este posto?")) return;
        try {
          await api.delete(`/stations/${station.id}`);
          showMessage(status, "Posto desativado.", "success");
          await searchStations();
        } catch (error) {
          showMessage(status, error.message, "error");
        }
      });
      row.append(summary, deactivate);
      host.append(row);
    });
  } catch (error) {
    showMessage(status, error.message, "error");
  }
}

function setupPrices() {
  document.getElementById("price-station-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    const query = document.getElementById("price-station-query").value.trim();
    try {
      const stations = await api.get(query
        ? `/stations?query=${encodeURIComponent(query)}`
        : "/stations");
      const select = document.getElementById("price-station-id");
      select.replaceChildren(makeElement("option", "Selecione um posto"));
      stations.forEach((station) => {
        const option = makeElement("option", `${station.tradeName || station.corporateName} · ${station.city}`);
        option.value = station.id;
        select.append(option);
      });
      document.getElementById("price-entry").classList.toggle("hidden", !stations.length);
      if (!stations.length) showMessage(status, "Nenhum posto encontrado.", "error");
      else showMessage(status, `${stations.length} posto(s) disponível(is).`, "success");
    } catch (error) {
      showMessage(status, error.message, "error");
    }
  });
  document.getElementById("price-station-id").addEventListener("change", loadStationPrices);
  document.getElementById("price-form").addEventListener("submit", createPrice);
}

async function loadStationPrices() {
  const stationId = document.getElementById("price-station-id").value;
  if (!stationId) return;
  try {
    const prices = await api.get(`/stations/${stationId}/fuel-prices`);
    const host = document.getElementById("admin-price-list");
    clearElement(host);
    prices.forEach((price) => {
      const row = makeElement("div", undefined, "price-row");
      row.append(makeElement("span", `${price.fuelTypeName} · coleta ${formatDate(price.collectionDate)}`));
      row.append(makeElement("strong", formatCurrency(price.saleValue, price.unitOfMeasure?.replace("R$/", "") || "")));
      const edit = makeElement("button", "Editar preço", "secondary");
      edit.type = "button";
      edit.addEventListener("click", () => editPrice(price));
      row.append(edit);
      host.append(row);
    });
    if (!prices.length) host.append(makeElement("p", "Este posto não possui preços registrados.", "muted"));
  } catch (error) {
    showMessage(status, error.message, "error");
  }
}

async function editPrice(price) {
  const saleValue = window.prompt("Novo preço:", String(price.saleValue));
  if (saleValue === null) return;
  const collectionDate = window.prompt("Data da coleta (AAAA-MM-DD):", price.collectionDate);
  if (collectionDate === null) return;
  const stationId = document.getElementById("price-station-id").value;
  try {
    await api.patch(`/stations/${stationId}/fuel-prices/${price.id}`, {
      saleValue: Number(saleValue),
      collectionDate,
    });
    showMessage(status, "Preço atualizado.", "success");
    await loadStationPrices();
  } catch (error) {
    showMessage(status, error.message, "error");
  }
}

async function createPrice(event) {
  event.preventDefault();
  const form = new FormData(event.currentTarget);
  const stationId = document.getElementById("price-station-id").value;
  try {
    await api.post(`/stations/${stationId}/fuel-prices`, {
      fuelTypeCode: form.get("fuelTypeCode"),
      saleValue: Number(form.get("saleValue")),
      collectionDate: form.get("collectionDate"),
    });
    event.currentTarget.reset();
    showMessage(status, "Preço registrado.", "success");
    await loadStationPrices();
  } catch (error) {
    showMessage(status, error.message, "error");
  }
}

function setupReviews() {
  document.getElementById("review-status-filter").addEventListener("change", loadReviews);
  document.getElementById("refresh-reviews").addEventListener("click", loadReviews);
  document.getElementById("review-moderation-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const id = String(form.get("reviewId")).trim();
    if (!/^[0-9a-f-]{36}$/i.test(id)) {
      showMessage(status, "Informe um UUID válido para a avaliação.", "error");
      return;
    }
    try {
      const result = await api.patch(`/reviews/${id}/moderation`, { status: form.get("reviewStatus") });
      showMessage(status, `Avaliação ${result.status === "APPROVED" ? "aprovada" : "rejeitada"}.`, "success");
      await loadReviews();
    } catch (error) {
      showMessage(status, error.message, "error");
    }
  });
  loadReviews();
}

async function loadReviews() {
  const selectedStatus = document.getElementById("review-status-filter").value;
  try {
    const reviews = await api.get(`/admin/reviews?status=${encodeURIComponent(selectedStatus)}`);
    const host = document.getElementById("moderation-list");
    clearElement(host);
    if (!reviews.length) host.append(makeElement("p", "Nenhuma avaliação com este status.", "muted"));
    reviews.forEach((review) => {
      const row = makeElement("article", undefined, "admin-row");
      const details = makeElement("div");
      details.append(makeElement("strong", `${review.userName} · ${review.rating}/5`));
      details.append(makeElement("div", review.comment || "Sem comentário.", "muted"));
      details.append(makeElement("code", review.id));
      const moderate = makeElement("button", "Selecionar", "secondary");
      moderate.type = "button";
      moderate.addEventListener("click", () => {
        document.getElementById("review-id").value = review.id;
        document.getElementById("review-status").focus();
      });
      row.append(details, moderate);
      host.append(row);
    });
  } catch (error) {
    showMessage(status, error.message, "error");
  }
}

function setupAnp() {
  const form = document.getElementById("anp-import-form");
  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    const values = new FormData(form);
    showMessage(status, "Executando importação ANP…");
    try {
      const result = await api.post("/admin/anp/import", {
        sourceUrl: values.get("sourceUrl"),
        referencePeriod: values.get("referencePeriod"),
      });
      renderImportResult(result);
      form.reset();
      await loadImportHistory();
      showMessage(status, `Importação finalizada: ${result.status}.`, result.status === "FAILED" ? "error" : "success");
    } catch (error) {
      showMessage(status, error.message, "error");
    }
  });
  document.getElementById("refresh-imports").addEventListener("click", loadImportHistory);
  loadImportHistory();
}

async function loadImportHistory() {
  try {
    const imports = await api.get("/admin/anp/imports");
    const host = document.getElementById("import-history");
    clearElement(host);
    if (!imports.length) host.append(makeElement("p", "Nenhuma importação registrada.", "muted"));
    imports.forEach((item) => {
      const row = makeElement("article", undefined, "admin-row");
      row.append(makeElement("div", `${item.fileName} · ${item.referencePeriod} · ${formatDate(item.importStart)}`));
      row.append(makeElement("span", `${item.status} · ${item.totalRecordsImported}/${item.totalRecordsRead}`, "pill"));
      host.append(row);
    });
  } catch (error) {
    showMessage(status, error.message, "error");
  }
}

function renderImportResult(result) {
  const host = document.getElementById("import-result");
  host.replaceChildren();
  host.append(makeElement("h3", `Status: ${result.status}`));
  host.append(makeElement("p", `Lidos: ${result.totalRecordsRead} · Importados: ${result.totalRecordsImported}`));
  if (result.errorDetails) host.append(makeElement("pre", result.errorDetails));
  host.classList.remove("hidden");
}
