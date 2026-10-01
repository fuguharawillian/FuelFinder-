import { initializeShell } from "./shell.js";
import { getCurrentUser } from "./auth.js";
import { api } from "./api.js";
import { formatCurrency, formatDate, makeElement, showMessage } from "./ui.js";

const params = new URLSearchParams(window.location.search);
const stationId = params.get("id");
const status = document.getElementById("page-status");

document.addEventListener("DOMContentLoaded", async () => {
  const user = await initializeShell();
  if (!stationId || !/^[0-9a-f-]{36}$/i.test(stationId)) {
    showMessage(status, "O identificador do posto é inválido.", "error");
    return;
  }
  await loadDetails(user);
});

async function loadDetails(user) {
  showMessage(status, "Carregando informações do posto…");
  try {
    const [station, prices, reviews] = await Promise.all([
      api.get(`/stations/${stationId}`),
      api.get(`/stations/${stationId}/fuel-prices`),
      api.get(`/stations/${stationId}/reviews`),
    ]);
    document.getElementById("station-name").textContent = station.tradeName || station.corporateName;
    document.getElementById("station-address").textContent = [station.address, station.city, station.state, station.postalCode].filter(Boolean).join(" · ");
    document.getElementById("station-rating").textContent = `${Number(station.averageRating || 0).toFixed(1)} ★ · ${station.totalReviews || 0} avaliações`;
    renderPrices(prices);
    renderReviews(reviews, user);
    renderRoutes(station);
    document.getElementById("review-section").classList.toggle("hidden", user?.role !== "ROLE_DRIVER");
    if (user?.role === "ROLE_DRIVER") setupReviewForm();
    showMessage(status, "Informações atualizadas. Os preços são históricos; confira a data da coleta.", "success");
  } catch (error) {
    showMessage(status, error.message, "error");
  }
}

function renderPrices(prices) {
  const host = document.getElementById("price-list");
  host.replaceChildren();
  if (!prices.length) {
    host.append(makeElement("p", "Não há preços cadastrados para este posto.", "muted"));
    return;
  }
  prices.forEach((price) => {
    const row = makeElement("div", undefined, "price-row");
    const detail = makeElement("div");
    detail.append(makeElement("strong", price.fuelTypeName || price.fuelTypeCode));
    detail.append(makeElement("div", `Coletado em ${formatDate(price.collectionDate)} · ${price.dataSource}` , "muted"));
    row.append(detail, makeElement("div", formatCurrency(price.saleValue, price.unitOfMeasure?.replace("R$/", "") || ""), "price"));
    host.append(row);
  });
}

function renderReviews(reviews, user) {
  const host = document.getElementById("review-list");
  host.replaceChildren();
  if (!reviews.length) {
    host.append(makeElement("p", "Ainda não há avaliações publicadas.", "muted"));
    return;
  }
  reviews.forEach((review) => {
    const row = makeElement("article", undefined, "review-row");
    const content = makeElement("div");
    content.append(makeElement("strong", review.userName || "Motorista"));
    content.append(makeElement("div", `${"★".repeat(review.rating)}${"☆".repeat(5 - review.rating)} · ${formatDate(review.createdAt)}`, "rating"));
    content.append(makeElement("p", review.comment || "Sem comentário."));
    row.append(content);
    if (user?.id === review.userId) {
      const actions = makeElement("div", undefined, "form-actions");
      const edit = makeElement("button", "Editar comentário", "secondary");
      edit.type = "button";
      edit.addEventListener("click", () => editReview(review));
      const remove = makeElement("button", "Excluir", "danger");
      remove.type = "button";
      remove.addEventListener("click", () => deleteReview(review.id));
      actions.append(edit, remove);
      row.append(actions);
    }
    host.append(row);
  });
}

function renderRoutes(station) {
  const host = document.getElementById("route-links");
  host.replaceChildren();
  if (station.latitude === null || station.longitude === null) {
    host.append(makeElement("p", "Este posto ainda não possui coordenadas para gerar rotas.", "muted"));
    return;
  }
  const coords = `${station.latitude},${station.longitude}`;
  host.append(routeLink("Google Maps", `https://www.google.com/maps/dir/?api=1&destination=${coords}`));
  host.append(routeLink("Waze", `https://waze.com/ul?ll=${coords}&navigate=yes`));
}

function routeLink(label, href) {
  const anchor = makeElement("a", `Abrir no ${label}`, "button secondary");
  anchor.href = href;
  anchor.target = "_blank";
  anchor.rel = "noopener noreferrer";
  return anchor;
}

function setupReviewForm() {
  const form = document.getElementById("review-form");
  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    const data = new FormData(form);
    try {
      await api.post(`/stations/${stationId}/reviews`, {
        rating: Number(data.get("rating")),
        comment: data.get("comment"),
      });
      form.reset();
      showMessage(document.getElementById("review-status"), "Avaliação enviada para moderação.", "success");
      const current = getCurrentUser();
      renderReviews(await api.get(`/stations/${stationId}/reviews`), current);
    } catch (error) {
      showMessage(document.getElementById("review-status"), error.message, "error");
    }
  });
}

async function editReview(review) {
  const rating = window.prompt("Nova nota de 1 a 5:", String(review.rating));
  if (rating === null) return;
  const comment = window.prompt("Novo comentário (máximo 500 caracteres):", review.comment || "");
  if (comment === null) return;
  try {
    await api.patch(`/reviews/${review.id}`, { rating: Number(rating), comment });
    showMessage(status, "Avaliação atualizada.", "success");
    renderReviews(await api.get(`/stations/${stationId}/reviews`), getCurrentUser());
  } catch (error) {
    showMessage(status, error.message, "error");
  }
}

async function deleteReview(reviewId) {
  if (!window.confirm("Deseja realmente excluir sua avaliação?")) return;
  try {
    await api.delete(`/reviews/${reviewId}`);
    showMessage(status, "Avaliação excluída.", "success");
    renderReviews(await api.get(`/stations/${stationId}/reviews`), getCurrentUser());
  } catch (error) {
    showMessage(status, error.message, "error");
  }
}
