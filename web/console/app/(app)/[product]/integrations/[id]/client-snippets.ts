export type OpenMode = "redirect" | "incontext";

export type ClientLibrary = "javascript" | "react" | "vue" | "angular" | "svelte";

export const CLIENT_LIBRARIES: { id: ClientLibrary; label: string }[] = [
  { id: "javascript", label: "JavaScript" },
  { id: "react", label: "React" },
  { id: "vue", label: "Vue" },
  { id: "angular", label: "Angular" },
  { id: "svelte", label: "Svelte" },
];

function quoted(value: string): string {
  return `"${value.replace(/\\/g, "\\\\").replace(/"/g, '\\"')}"`;
}

function loader(scriptUrl: string, loadError: string): string {
  const src = quoted(scriptUrl);
  const message = quoted(loadError);
  return `function loadRecognizMe() {
  if (window.RecognizMe) return Promise.resolve();
  return new Promise(function (resolve, reject) {
    var found = document.querySelector("script[data-recogniz-me]");
    if (found && found.getAttribute("data-recogniz-me-state") === "error") {
      reject(new Error(${message}));
      return;
    }
    var script = found || document.createElement("script");
    if (!found) {
      script.setAttribute("src", ${src});
      script.setAttribute("async", "true");
      script.setAttribute("data-recogniz-me", "");
      document.head.appendChild(script);
    }
    script.addEventListener("load", function () { resolve(); });
    script.addEventListener("error", function () {
      script.setAttribute("data-recogniz-me-state", "error");
      reject(new Error(${message}));
    });
  });
}

function openRecognizMe(options) {
  return loadRecognizMe().then(function () {
    return window.RecognizMe.open(options);
  });
}`;
}

function typedOpen(): string {
  return `function openRecognizMe(options: {
  mode: string;
  endpoint: string;
  externalId?: string;
  firstName?: string;
  lastName?: string;
  email?: string;
}): Promise<void> {
  return loadRecognizMe().then(function () {
    return window.RecognizMe.open(options);
  });
}`;
}

function modeValue(mode: OpenMode): string {
  return mode === "incontext" ? "incontext" : "redirect";
}

function openCall(mode: OpenMode, fields: string): string {
  return `openRecognizMe({
      mode: "${modeValue(mode)}",
      endpoint: "/api/verifications",
      ${fields}
    })`;
}

export function clientSnippet(
  library: ClientLibrary,
  mode: OpenMode,
  buttonComment: string,
  buttonLabel: string,
  scriptUrl: string,
  loadError: string,
): string {
  const literal = openCall(
    mode,
    `externalId: "order-123",
      firstName: "Ada",
      lastName: "Lovelace",
      email: "ada@example.com",`,
  );
  const bound = openCall(
    mode,
    `externalId,
      firstName,
      lastName,
      email,`,
  );
  const load = loader(scriptUrl, loadError);

  switch (library) {
    case "javascript":
      return `<button type="button" id="verify">${buttonLabel}</button>
<script>
  ${load.replaceAll("\n", "\n  ")}

  // ${buttonComment}
  document.getElementById("verify").addEventListener("click", function () {
    ${literal.replaceAll("\n", "\n    ")}.catch(function (error) {
      console.error(error.message);
    });
  });
</script>`;
    case "react":
      return `${load}

// ${buttonComment}
export function Verify({ externalId, firstName, lastName, email }) {
  function verify() {
    ${bound}.catch(function (error) {
      console.error(error.message);
    });
  }

  return (
    <button type="button" onClick={verify}>
      ${buttonLabel}
    </button>
  );
}`;
    case "vue":
      return `<script setup>
${load}

// ${buttonComment}
const props = defineProps(["externalId", "firstName", "lastName", "email"]);

function verify() {
  openRecognizMe({
    mode: "${modeValue(mode)}",
    endpoint: "/api/verifications",
    externalId: props.externalId,
    firstName: props.firstName,
    lastName: props.lastName,
    email: props.email,
  }).catch((error) => console.error(error.message));
}
</script>

<template>
  <button type="button" @click="verify">${buttonLabel}</button>
</template>`;
    case "angular":
      return `import { Component, Input } from "@angular/core";

declare global {
  interface Window {
    RecognizMe: {
      open(options: {
        mode: string;
        endpoint: string;
        externalId?: string;
        firstName?: string;
        lastName?: string;
        email?: string;
      }): Promise<void>;
    };
  }
}

${load.replace("function openRecognizMe(options) {\n  return loadRecognizMe().then(function () {\n    return window.RecognizMe.open(options);\n  });\n}", typedOpen())}

// ${buttonComment}
@Component({
  selector: "app-verify",
  template: '<button type="button" (click)="verify()">${buttonLabel}</button>',
})
export class VerifyComponent {
  @Input() externalId?: string;
  @Input() firstName?: string;
  @Input() lastName?: string;
  @Input() email?: string;

  verify() {
    openRecognizMe({
      mode: "${modeValue(mode)}",
      endpoint: "/api/verifications",
      externalId: this.externalId,
      firstName: this.firstName,
      lastName: this.lastName,
      email: this.email,
    }).catch((error: { message?: string }) => console.error(error.message));
  }
}`;
    case "svelte":
      return `<script>
  ${load.replaceAll("\n", "\n  ")}

  // ${buttonComment}
  export let externalId;
  export let firstName;
  export let lastName;
  export let email;

  function verify() {
    ${bound}.catch((error) => console.error(error.message));
  }
</script>

<button type="button" on:click={verify}>${buttonLabel}</button>`;
  }
}
