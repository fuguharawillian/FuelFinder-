import { getCurrentUser, isAdmin, logout, restoreSession, setSessionExpirationHandler } from "./auth.js";
import { makeElement, showMessage } from "./ui.js";

const navItems = [
  { href: "/index.html", label: "Encontrar postos" },
  { href: "/vehicles.html", label: "Meus veículos", role: "ROLE_DRIVER" },
  { href: "/recommendations.html", label: "Recomendações", role: "ROLE_DRIVER" },
  { href: "/admin/index.html", label: "Administração", role: "ROLE_ADMIN" },
];

export async function initializeShell({ requiredRole } = {}) {
  const status = document.querySelector("[data-shell-status]");
  try {
    await restoreSession();
  } catch {
    if (status) showMessage(status, "Não foi possível restaurar sua sessão. Algumas ações exigirão novo acesso.", "error");
  }

  setSessionExpirationHandler(() => {
    if (requiredRole) window.location.assign("/login.html?next=" + encodeURIComponent(window.location.pathname));
  });
  renderNavigation();

  const user = getCurrentUser();
  if (requiredRole && !user) {
    if (status) showMessage(status, "É necessário entrar para acessar esta página.", "error");
    window.setTimeout(() => window.location.assign("/login.html"), 700);
    return null;
  }
  if (requiredRole === "ROLE_ADMIN" && !isAdmin()) {
    if (status) showMessage(status, "Esta página está disponível somente para administradores.", "error");
    window.setTimeout(() => window.location.assign("/index.html"), 900);
    return null;
  }
  if (requiredRole === "ROLE_DRIVER" && user?.role !== "ROLE_DRIVER") {
    if (status) showMessage(status, "Esta página está disponível somente para motoristas.", "error");
    window.setTimeout(() => window.location.assign("/index.html"), 900);
    return null;
  }
  return user;
}

function renderNavigation() {
  const host = document.querySelector("[data-site-nav]");
  if (!host) return;
  const user = getCurrentUser();
  const nav = makeElement("nav", undefined, "site-nav");
  nav.setAttribute("aria-label", "Navegação principal");

  const brand = makeElement("a", "FuelFinder", "brand");
  brand.href = "/index.html";
  nav.append(brand);

  const links = makeElement("div", undefined, "nav-links");
  navItems.forEach((item) => {
    if (item.role && user?.role !== item.role) return;
    const link = makeElement("a", item.label);
    link.href = item.href;
    links.append(link);
  });
  if (user) {
    links.append(makeElement("span", user.fullName, "nav-user"));
    const signOut = makeElement("button", "Sair", "secondary");
    signOut.type = "button";
    signOut.setAttribute("aria-label", "Sair da conta");
    signOut.addEventListener("click", async () => {
      try {
        await logout();
      } catch {
        window.location.assign("/login.html");
      }
    });
    links.append(signOut);
  } else {
    const signIn = makeElement("a", "Entrar", "button");
    signIn.href = "/login.html";
    links.append(signIn);
  }
  nav.append(links);
  host.replaceChildren(nav);
}
