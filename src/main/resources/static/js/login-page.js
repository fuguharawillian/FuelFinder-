import { initializeShell } from "./shell.js";
import { login, register } from "./auth.js";
import { showMessage } from "./ui.js";

document.addEventListener("DOMContentLoaded", async () => {
  await initializeShell();
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
      const safeNext = next && next.startsWith("/") && !next.startsWith("//") ? next : "/index.html";
      window.location.assign(safeNext);
    } catch (error) {
      showMessage(message, error.message, "error");
    }
  });
});

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
