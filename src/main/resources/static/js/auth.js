import { api, getAccessToken, setAccessToken, setUnauthorizedHandler } from "./api.js";

let currentUser = null;

export function getCurrentUser() {
  return currentUser;
}

export function isAuthenticated() {
  return Boolean(getAccessToken() && currentUser);
}

export function isAdmin() {
  return currentUser?.role === "ROLE_ADMIN";
}

export async function restoreSession() {
  try {
    const data = await api.post("auth/refresh");
    if (!data?.accessToken || !data.user) return false;
    setAccessToken(data.accessToken);
    currentUser = data.user;
    return true;
  } catch (error) {
    if (error.status === 401) {
      clearSession();
      return false;
    }
    throw error;
  }
}

export async function login(email, password) {
  const data = await api.post("auth/login", { email, password });
  setAuthenticatedSession(data);
  return data.user;
}

export async function register(fullName, email, password) {
  const data = await api.post("auth/register", { fullName, email, password });
  setAuthenticatedSession(data);
  return data.user;
}

export async function logout() {
  try {
    await api.post("auth/logout");
  } finally {
    clearSession();
    window.location.assign("/login.html");
  }
}

export function clearSession() {
  setAccessToken(null);
  currentUser = null;
}

export function setSessionExpirationHandler(handler) {
  setUnauthorizedHandler(handler);
}

function setAuthenticatedSession(data) {
  setAccessToken(data.accessToken);
  currentUser = data.user;
}
