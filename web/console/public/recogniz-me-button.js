/**
 * Bouton natif Recogniz-Me.
 * Le client ne dessine pas le bouton, le sceau, ni la mention.
 * Au clic, l'élément appelle l'endpoint du client et ouvre hosted_url.
 */
(function () {
  if (customElements.get("recogniz-me-button")) return;

  var COPY = {
    en: "Identity verification is performed by Recogniz-Me.",
    fr: "La vérification d’identité est réalisée par Recogniz-Me.",
  };

  var LOGO =
    '<svg width="16" height="16" viewBox="0 0 32 32" aria-hidden="true">' +
    '<rect width="32" height="32" rx="9" fill="#0f7a4d"></rect>' +
    '<circle cx="16" cy="16" r="10.5" fill="none" stroke="#ffffff" stroke-opacity="0.35" stroke-width="1.5"></circle>' +
    '<path d="M10.7 22.15L10.7 9.55A6.45 3.8 0 0 1 10.7 17.15M12.35 17.05L15.45 21.35L22.05 11.95" fill="none" stroke="#ffffff" stroke-width="2.6" stroke-linecap="round" stroke-linejoin="round"></path>' +
    "</svg>";

  var STYLE = [
    ":host{display:block;width:min(100%,16rem);font-family:ui-sans-serif,system-ui,-apple-system,'Segoe UI',Roboto,sans-serif;}",
    ".card{display:grid;justify-items:center;gap:1rem;padding:1.5rem 1.25rem;border:1px solid #dde5e0;border-radius:14px;background:#fff;box-shadow:0 1px 2px rgba(6,53,35,.06);}",
    "button{width:100%;margin:0;padding:.75rem 1rem;border:0;border-radius:10px;background:#0f7a4d;color:#fff;font:inherit;font-weight:550;cursor:pointer;}",
    "button:hover:not(:disabled){background:#0b6340;}",
    "button:disabled{opacity:.55;cursor:wait;}",
    ".provider{display:flex;flex-direction:column;align-items:center;gap:.5rem;margin:0;color:#5a6b62;font-size:.75rem;line-height:1.45;text-align:center;}",
  ].join("");

  function text(lang) {
    return COPY[String(lang || "").toLowerCase().slice(0, 2)] || COPY.en;
  }

  function bodyOf(el) {
    var body = {};
    var externalId = el.getAttribute("external-id");
    var firstName = el.getAttribute("first-name");
    var lastName = el.getAttribute("last-name");
    var email = el.getAttribute("email");
    if (externalId) body.externalId = externalId;
    if (firstName) body.firstName = firstName;
    if (lastName) body.lastName = lastName;
    if (email) body.email = email;
    return body;
  }

  class RecognizMeButton extends HTMLElement {
    connectedCallback() {
      if (this.shadowRoot) return;
      var root = this.attachShadow({ mode: "open" });
      var lang = this.getAttribute("lang") || document.documentElement.lang;
      root.innerHTML =
        "<style>" +
        STYLE +
        "</style>" +
        '<div class="card">' +
        '<button type="button">Get verified</button>' +
        '<p class="provider">' +
        LOGO +
        "<span>" +
        text(lang) +
        "</span></p></div>";
      root.querySelector("button").addEventListener("click", () => this.open());
    }

    async open() {
      if (this.hasAttribute("preview")) return;
      var button = this.shadowRoot.querySelector("button");
      button.disabled = true;
      try {
        var response = await fetch(this.getAttribute("endpoint") || "/api/verifications", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(bodyOf(this)),
        });
        var session = await response.json();
        var url = session.hosted_url;
        if (!response.ok || !url) {
          this.dispatchEvent(
            new CustomEvent("recogniz-error", {
              bubbles: true,
              detail: session.error || "La session n’a pas pu être créée.",
            }),
          );
          return;
        }
        if (this.getAttribute("mode") === "incontext") {
          this.frame(url);
        } else {
          window.location.assign(url);
        }
      } finally {
        button.disabled = false;
      }
    }

    frame(url) {
      var existing = document.getElementById("recogniz-me-frame");
      if (existing) existing.remove();
      var overlay = document.createElement("div");
      overlay.id = "recogniz-me-frame";
      overlay.setAttribute(
        "style",
        "position:fixed;inset:0;z-index:2147483000;display:grid;place-items:center;padding:1rem;background:rgba(17,26,21,.45);",
      );
      var frame = document.createElement("iframe");
      frame.setAttribute(
        "style",
        "width:min(28rem,100%);height:min(44rem,100%);border:0;border-radius:14px;background:#fff;",
      );
      frame.src = url;
      frame.title = "Recogniz-Me";
      frame.allow = "camera; microphone";
      overlay.appendChild(frame);
      overlay.addEventListener("click", function (event) {
        if (event.target === overlay) overlay.remove();
      });
      document.body.appendChild(overlay);
    }
  }

  customElements.define("recogniz-me-button", RecognizMeButton);
})();
