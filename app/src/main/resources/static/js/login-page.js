import { initializeShell } from "./shell.js";
import { login, register } from "./auth.js";
import { showMessage } from "./ui.js";

document.addEventListener("DOMContentLoaded", async () => {
  const user = await initializeShell();
  if (user) {
    const next = new URLSearchParams(window.location.search).get("next");
    window.location.replace(resolveNextPath(next));
    return;
  }
  const form = document.getElementById("auth-form");
  const message = document.getElementById("page-status");
  const mode = new URLSearchParams(window.location.search).get("mode");
  setMode(mode === "register");
  document.getElementById("mode-toggle").addEventListener("click", () => {
    setMode(document.getElementById("full-name").closest("label").classList.contains("hidden"));
  });

  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    const data = new FormData(form);
    const isRegister = !document.getElementById("full-name").closest("label").classList.contains("hidden");
    showMessage(message, isRegister ? "Criando sua conta…" : "Entrando…");
    try {
      if (isRegister) {
        await register(data.get("fullName"), data.get("email"), data.get("password"));
      } else {
        await login(data.get("email"), data.get("password"));
      }
      const next = new URLSearchParams(window.location.search).get("next");
      window.location.assign(resolveNextPath(next));
    } catch (error) {
      showMessage(message, error.message, "error");
    }
  });
});

function resolveNextPath(next) {
  const fallback = "/map.html";
  if (!next) return fallback;
  try {
    const target = new URL(next, window.location.origin);
    const isAllowedPath = target.pathname === "/map.html"
      || target.pathname === "/vehicles.html"
      || target.pathname === "/recommendations.html"
      || target.pathname === "/station-detail.html"
      || /^\/admin\/[a-z-]+\.html$/.test(target.pathname);
    if (target.origin !== window.location.origin || !isAllowedPath) return fallback;
    return `${target.pathname}${target.search}${target.hash}`;
  } catch {
    return fallback;
  }
}

function setMode(registerMode) {
  const fullName = document.getElementById("full-name").closest("label");
  const password = document.getElementById("password");
  const submit = document.getElementById("auth-submit");
  const toggle = document.getElementById("mode-toggle");
  fullName.classList.toggle("hidden", !registerMode);
  document.getElementById("full-name").required = registerMode;
  password.minLength = registerMode ? 8 : 1;
  submit.textContent = registerMode ? "Criar conta" : "Entrar";
  toggle.textContent = registerMode ? "Já tenho conta" : "Criar conta";
  document.getElementById("auth-heading").textContent = registerMode ? "Crie sua conta" : "Acesse sua conta";
}
