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
      const deactivate = makeElement("button", "Desativar", "btn btn-outline-danger");
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
      const edit = makeElement("button", "Editar preço", "btn btn-outline-primary");
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
      const moderate = makeElement("button", "Selecionar", "btn btn-outline-primary");
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
  const submitButton = form.querySelector('button[type="submit"]');
  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    const values = new FormData(form);
    submitButton.disabled = true;
    showMessage(status, "Iniciando importação ANP…");
    try {
      let result = await api.post("/admin/anp/import", {
        sourceUrl: values.get("sourceUrl"),
        referencePeriod: values.get("referencePeriod"),
      });
      renderImportResult(result);
      while (result.status === "RUNNING") {
        await new Promise((resolve) => window.setTimeout(resolve, 1200));
        result = await api.get(`/admin/anp/imports/${encodeURIComponent(result.id)}`);
        renderImportResult(result);
        await loadImportHistory();
      }
      await loadImportHistory();
      showMessage(
        status,
        `Importação finalizada: ${result.status}.`,
        result.status === "FAILED" ? "error" : result.status === "PARTIAL" ? "warning" : "success",
      );
    } catch (error) {
      showMessage(status, error.message, "error");
    } finally {
      submitButton.disabled = false;
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
      const progress = item.progressPercent == null ? "em andamento" : `${item.progressPercent}%`;
      const summary = `${item.status} · ${item.totalRecordsImported}/${item.totalRecordsRead} · ${progress}`;
      row.append(makeElement("span", summary, "pill"));
      if (item.status === "RUNNING" && item.progressMessage) {
        row.append(makeElement("small", item.progressMessage, "muted"));
      }
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
  if (result.progressMessage) host.append(makeElement("p", result.progressMessage));
  const progress = makeElement("div", undefined, "progress");
  progress.setAttribute("role", "progressbar");
  progress.setAttribute("aria-label", "Progresso da importação ANP");
  const bar = makeElement("div", undefined, "progress-bar progress-bar-striped");
  if (result.status === "RUNNING") bar.classList.add("progress-bar-animated");
  if (Number.isInteger(result.progressPercent)) {
    bar.style.width = `${Math.max(0, Math.min(100, result.progressPercent))}%`;
    bar.setAttribute("aria-valuenow", String(result.progressPercent));
    bar.setAttribute("aria-valuemin", "0");
    bar.setAttribute("aria-valuemax", "100");
  } else {
    bar.style.width = "100%";
    bar.setAttribute("aria-busy", "true");
  }
  progress.append(bar);
  host.append(progress);

  const summary = makeElement("ul", undefined, "small mb-0");
  [
    `Linhas lidas do CSV: ${result.totalRecordsRead ?? 0}`,
    `Linhas SP associadas: ${result.rowsImportedFromSaoPaulo ?? 0}`,
    `Linhas ignoradas por outro estado: ${result.rowsIgnoredOtherStates ?? 0}`,
    `Linhas inválidas: ${result.invalidRows ?? 0}`,
    `Postos criados / atualizados: ${result.stationsCreated ?? 0} / ${result.stationsUpdated ?? 0}`,
    `Preços associados (CNPJ + combustível): ${result.pricesAssociated ?? 0}`,
    `Preços novos ou atualizados: ${result.totalRecordsImported ?? 0}`,
    `Coordenadas atualizadas pela API: ${result.coordinatesUpdated ?? 0}`,
    `CNPJs sem correspondência na API: ${result.apiCnpjsUnmatched ?? 0}`,
    `Registros da API sem coordenadas válidas: ${result.apiStationsWithoutCoordinates ?? 0}`,
    `Postos ainda sem coordenadas: ${result.stationsWithoutCoordinates ?? 0}`,
    `Páginas da API processadas: ${result.apiPagesProcessed ?? 0}`,
  ].forEach((text) => summary.append(makeElement("li", text)));
  host.append(summary);
  if (result.errorDetails) {
    const details = makeElement("pre", result.errorDetails, "small text-wrap");
    details.style.maxHeight = "16rem";
    details.style.overflowY = "auto";
    host.append(details);
  }
  host.classList.remove("hidden");
}
