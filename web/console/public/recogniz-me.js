/**
 * Plugin UI Recogniz-Me.
 * L'intégrateur dessine son bouton. Au clic, il appelle RecognizMe.open.
 * Le plugin crée la session sur le serveur de l'intégrateur, puis ouvre hosted_url.
 */
(function () {
  if (window.RecognizMe) return;

  var FAILURE = "La session n’a pas pu être créée.";

  function text(value) {
    return typeof value === "string" ? value.trim() : "";
  }

  function payload(options) {
    var body = {};
    var externalId = text(options.externalId);
    var firstName = text(options.firstName);
    var lastName = text(options.lastName);
    var email = text(options.email);
    if (externalId) body.externalId = externalId;
    if (firstName) body.firstName = firstName;
    if (lastName) body.lastName = lastName;
    if (email) body.email = email;
    return body;
  }

  function failureMessage(session) {
    if (!session || typeof session !== "object") return FAILURE;
    if (typeof session.error === "string" && session.error) return session.error;
    if (session.error && typeof session.error.message === "string" && session.error.message) {
      return session.error.message;
    }
    return FAILURE;
  }

  function close() {
    var existing = document.getElementById("recogniz-me-frame");
    if (existing) existing.remove();
  }

  function frame(url) {
    close();
    var overlay = document.createElement("div");
    overlay.id = "recogniz-me-frame";
    overlay.setAttribute(
      "style",
      "position:fixed;inset:0;z-index:2147483000;display:grid;place-items:center;padding:1rem;background:rgba(17,26,21,.45);",
    );
    var pane = document.createElement("iframe");
    pane.setAttribute(
      "style",
      "width:min(28rem,100%);height:min(44rem,100%);border:0;border-radius:14px;background:#fff;",
    );
    pane.src = url;
    pane.title = "Recogniz-Me";
    pane.allow = "camera; microphone";
    overlay.appendChild(pane);
    overlay.addEventListener("click", function (event) {
      if (event.target === overlay) close();
    });
    document.body.appendChild(overlay);
  }

  function open(options) {
    var settings = options || {};
    return fetch(settings.endpoint || "/api/verifications", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload(settings)),
    })
      .then(function (response) {
        return response.json().catch(function () {
          return {};
        }).then(function (session) {
          var url = session && session.hosted_url;
          if (!response.ok || !url) throw new Error(failureMessage(session));
          if (settings.mode === "incontext") frame(url);
          else window.location.assign(url);
        });
      });
  }

  window.RecognizMe = { open: open, close: close };
})();
