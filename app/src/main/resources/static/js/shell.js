import { clearSession, getCurrentUser, isAdmin, isAuthenticated, logout, restoreSession, setSessionExpirationHandler } from "./auth.js";
import { makeElement, showMessage } from "./ui.js";

const navItems = [
  { href: "/map.html", label: "Encontrar postos" },
  { href: "/vehicles.html", label: "Meus veículos", role: "ROLE_DRIVER" },
  { href: "/recommendations.html", label: "Recomendações", role: "ROLE_DRIVER" },
  { href: "/admin/index.html", label: "Administração", role: "ROLE_ADMIN" },
];
const publicNavItems = [
  { href: "#benefits-heading", label: "Benefícios" },
  { href: "#como-funciona", label: "Como funciona" },
];

export async function initializeShell({ requiredRole, requiredAuth = false } = {}) {
  const status = document.querySelector("[data-shell-status]");
  try {
    await restoreSession();
  } catch {
    if (status) showMessage(status, "Não foi possível restaurar sua sessão. Algumas ações exigirão novo acesso.", "error");
  }

  setSessionExpirationHandler(() => {
    clearSession();
    if (requiredAuth || requiredRole) {
      const next = encodeURIComponent(window.location.pathname + window.location.search);
      window.location.assign(`/login.html?next=${next}`);
    }
  });
  const host = document.querySelector("[data-site-nav]");
  if (host && !host.dataset.sessionListenerAdded) {
    window.addEventListener("fuelfinder:sessionchange", () => {
      renderNavigation();
      updateAuthContent();
    });
    document.addEventListener("keydown", (event) => {
      if (event.key !== "Escape") return;
      const toggle = host.querySelector(".nav-toggle");
      const links = host.querySelector(".nav-links");
      if (!toggle || !links?.classList.contains("is-open")) return;
      links.classList.remove("is-open");
      toggle.setAttribute("aria-expanded", "false");
      toggle.focus();
    });
    host.dataset.sessionListenerAdded = "true";
  }
  renderNavigation();
  updateAuthContent();

  const user = getCurrentUser();
  if ((requiredAuth || requiredRole) && !isAuthenticated()) {
    if (status) showMessage(status, "Entre na sua conta para acessar esta área.", "error");
    const next = encodeURIComponent(window.location.pathname + window.location.search);
    window.setTimeout(() => window.location.assign(`/login.html?next=${next}`), 700);
    return null;
  }
  if (requiredRole === "ROLE_ADMIN" && !isAdmin()) {
    if (status) showMessage(status, "Esta página está disponível somente para administradores.", "error");
    window.setTimeout(() => window.location.assign("/map.html"), 900);
    return null;
  }
  if (requiredRole === "ROLE_DRIVER" && user?.role !== "ROLE_DRIVER") {
    if (status) showMessage(status, "Esta página está disponível somente para motoristas.", "error");
    window.setTimeout(() => window.location.assign("/map.html"), 900);
    return null;
  }
  return user;
}

function renderNavigation() {
  const host = document.querySelector("[data-site-nav]");
  if (!host) return;
  const user = getCurrentUser();
  const authenticated = isAuthenticated();
  const pageType = document.body.dataset.pageType;
  const isPublicHome = pageType === "public-home" || window.location.pathname === "/" || window.location.pathname === "/index.html";
  const nav = makeElement("nav", undefined, "site-nav");
  nav.setAttribute("aria-label", "Navegação principal");

  const brand = makeElement("a", undefined, "brand");
  brand.href = "/";
  brand.append(makeElement("span", "F", "brand-mark"), makeElement("span", "FuelFinder"));
  nav.append(brand);

  const links = makeElement("div", undefined, "nav-links");
  links.id = "primary-navigation";
  links.setAttribute("data-nav-links", "");
  const visibleItems = authenticated
    ? navItems.filter((item) => !item.role || user?.role === item.role)
    : isPublicHome ? publicNavItems : [];
  visibleItems.forEach((item) => {
    const link = makeElement("a", item.label);
    link.href = item.href;
    if (window.location.hash === item.href || window.location.pathname === item.href
        || (item.href === "/admin/index.html" && window.location.pathname.startsWith("/admin/"))) {
      link.setAttribute("aria-current", "page");
    }
    links.append(link);
  });
  if (visibleItems.length) {
    const toggle = makeElement("button", "Menu", "nav-toggle");
    toggle.type = "button";
    toggle.setAttribute("aria-controls", links.id);
    toggle.setAttribute("aria-expanded", "false");
    toggle.addEventListener("click", () => {
      const expanded = toggle.getAttribute("aria-expanded") === "true";
      toggle.setAttribute("aria-expanded", String(!expanded));
      links.classList.toggle("is-open", !expanded);
    });
    links.addEventListener("click", (event) => {
      if (!(event.target instanceof HTMLAnchorElement)) return;
      links.classList.remove("is-open");
      toggle.setAttribute("aria-expanded", "false");
    });
    nav.append(toggle, links);

  }

  if (authenticated) {
    const account = makeElement("div", undefined, "nav-account");
    account.append(makeElement("span", user.fullName, "nav-user"));
    const signOut = makeElement("button", "Sair", "btn btn-outline-primary");
    signOut.type = "button";
    signOut.setAttribute("aria-label", "Sair da conta");
    signOut.addEventListener("click", async () => {
      try {
        await logout();
        window.location.assign("/");
      } catch (error) {
        if (document.querySelector("[data-shell-status]")) {
          showMessage(document.querySelector("[data-shell-status]"), error.message, "error");
        }
      }
    });
    account.append(signOut);
    nav.append(account);
  } else if (isPublicHome) {
    const account = makeElement("div", undefined, "nav-account");
    const signIn = makeElement("a", "Entrar", "btn btn-primary");
    signIn.href = "/login.html";
    account.append(signIn);
    nav.append(account);
  }
  host.replaceChildren(nav);
}

function updateAuthContent() {
  const authenticated = isAuthenticated();
  document.querySelectorAll("[data-authenticated-only]").forEach((element) => {
    element.classList.toggle("hidden", !authenticated);
  });
  document.querySelectorAll("[data-visitor-only]").forEach((element) => {
    element.classList.toggle("hidden", authenticated);
  });
}
