export function showMessage(element, message, type = "") {
  element.textContent = message;
  const alertType = type === "error" ? "danger" : type === "success" ? "success" : "info";
  element.className = `result-state alert alert-${alertType}${type ? ` ${type}` : ""}`;
}

export function clearElement(element) {
  element.replaceChildren();
}

export function makeElement(tag, text, className) {
  const element = document.createElement(tag);
  if (text !== undefined && text !== null) element.textContent = String(text);
  if (className) element.className = className;
  return element;
}

export function formatCurrency(value, unit = "") {
  const formatted = new Intl.NumberFormat("pt-BR", {
    style: "currency",
    currency: "BRL",
  }).format(Number(value));
  return unit ? `${formatted}/${unit}` : formatted;
}

export function formatDate(value) {
  if (!value) return "Data não informada";
  const [year, month, day] = value.slice(0, 10).split("-");
  return `${day}/${month}/${year}`;
}

export function safeExternalLink(url, text, className = "btn btn-outline-primary") {
  const anchor = makeElement("a", text, className);
  anchor.href = url;
  anchor.target = "_blank";
  anchor.rel = "noopener noreferrer";
  return anchor;
}

export function createFormField(labelText, name, type = "text", options = {}) {
  const label = document.createElement("label");
  label.append(document.createTextNode(labelText));
  const input = document.createElement(type === "textarea" ? "textarea" : "input");
  input.className = "form-control";
  input.name = name;
  input.id = name;
  if (type !== "textarea") input.type = type;
  if (options.required) input.required = true;
  if (options.min !== undefined) input.min = String(options.min);
  if (options.max !== undefined) input.max = String(options.max);
  if (options.step !== undefined) input.step = String(options.step);
  if (options.placeholder) input.placeholder = options.placeholder;
  if (options.maxLength !== undefined) input.maxLength = options.maxLength;
  label.append(input);
  return { label, input };
}
