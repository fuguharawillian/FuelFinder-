const COOKIE_AUTH_ENDPOINTS = new Set([
  "auth/register",
  "auth/login",
  "auth/refresh",
  "auth/logout",
  "auth/sessions/revoke-all",
]);
const BEARER_AUTH_ENDPOINTS = new Set([
  "auth/logout",
  "auth/sessions/revoke-all",
]);

let accessToken = null;
let refreshInFlight = null;
let unauthorizedHandler = () => {};

export class ApiError extends Error {
  constructor(message, status = 0) {
    super(message);
    this.name = "ApiError";
    this.status = status;
  }
}

export function setAccessToken(token) {
  accessToken = token || null;
}

export function getAccessToken() {
  return accessToken;
}

export function setUnauthorizedHandler(handler) {
  unauthorizedHandler = handler;
}

function normalizedEndpoint(endpoint) {
  return endpoint.replace(/^\/+/, "");
}

async function parseResponse(response) {
  if (response.status === 204) return null;
  const text = await response.text();
  if (!text) return null;
  try {
    return JSON.parse(text);
  } catch {
    return text;
  }
}

function errorMessage(payload, status) {
  if (payload && typeof payload === "object") {
    const message = payload.detail || payload.message || payload.title || `Erro ${status}`;
    if (!payload.invalidFields || typeof payload.invalidFields !== "object") return message;
    const fieldErrors = Object.entries(payload.invalidFields)
      .flatMap(([field, errors]) => {
        const label = ({
          email: "E-mail",
          password: "Senha",
          fullName: "Nome",
        })[field] || field;
        return (Array.isArray(errors) ? errors : [errors])
          .filter((error) => typeof error === "string" && error.trim())
          .map((error) => `${label}: ${error}`);
      });
    return fieldErrors.length ? `${message} ${fieldErrors.join("; ")}.` : message;
  }
  return typeof payload === "string" && payload.trim() ? payload : `Erro ${status}`;
}

async function refreshToken() {
  if (!refreshInFlight) {
    refreshInFlight = (async () => {
      try {
        let response;
        try {
          response = await fetch("/auth/refresh", {
            method: "POST",
            credentials: "include",
            headers: { Accept: "application/json" },
          });
        } catch {
          throw new ApiError(
            "Não foi possível conectar ao FuelFinder. Verifique a conexão e tente novamente.",
          );
        }
        if (response.status === 401) return false;
        const payload = await parseResponse(response);
        if (!response.ok) {
          throw new ApiError(errorMessage(payload, response.status), response.status);
        }
        if (!payload?.accessToken || !payload.user) {
          throw new ApiError("Resposta inválida ao restaurar a sessão.");
        }
        setAccessToken(payload.accessToken);
        return true;
      } finally {
        refreshInFlight = null;
      }
    })();
  }
  return refreshInFlight;
}

async function send(endpoint, options, allowRefresh) {
  const path = normalizedEndpoint(endpoint);
  const headers = new Headers(options.headers || {});
  headers.set("Accept", "application/json");
  if (options.body !== undefined && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }
  if (accessToken
      && (!COOKIE_AUTH_ENDPOINTS.has(path) || BEARER_AUTH_ENDPOINTS.has(path))) {
    headers.set("Authorization", `Bearer ${accessToken}`);
  }
  let response;
  try {
    response = await fetch(`/${path}`, {
      ...options,
      headers,
      credentials: COOKIE_AUTH_ENDPOINTS.has(path) ? "include" : "omit",
    });
  } catch {
    throw new ApiError("Não foi possível conectar ao FuelFinder. Verifique a conexão e tente novamente.");
  }

  if (response.status === 401 && allowRefresh && accessToken
      && (!COOKIE_AUTH_ENDPOINTS.has(path) || BEARER_AUTH_ENDPOINTS.has(path))) {
    if (await refreshToken()) return send(endpoint, options, false);
    setAccessToken(null);
    if (path !== "auth/logout") unauthorizedHandler();
    throw new ApiError("Sua sessão expirou. Entre novamente.", 401);
  }

  const payload = await parseResponse(response);
  if (!response.ok) throw new ApiError(errorMessage(payload, response.status), response.status);
  return payload;
}

export const api = {
  request: (endpoint, options = {}) => send(endpoint, options, true),
  get: (endpoint) => send(endpoint, { method: "GET" }, true),
  post: (endpoint, body) => send(endpoint, {
    method: "POST",
    ...(body === undefined ? {} : { body: JSON.stringify(body) }),
  }, true),
  patch: (endpoint, body) => send(endpoint, {
    method: "PATCH",
    body: JSON.stringify(body),
  }, true),
  delete: (endpoint) => send(endpoint, { method: "DELETE" }, true),
};
