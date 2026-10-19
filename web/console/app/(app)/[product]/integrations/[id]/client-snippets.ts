export type OpenMode = "redirect" | "incontext";

export type ClientLibrary = "javascript" | "react" | "vue" | "angular" | "svelte";

export const CLIENT_LIBRARIES: { id: ClientLibrary; label: string }[] = [
  { id: "javascript", label: "JavaScript" },
  { id: "react", label: "React" },
  { id: "vue", label: "Vue" },
  { id: "angular", label: "Angular" },
  { id: "svelte", label: "Svelte" },
];

const SCRIPT = "https://js.recogniz.me/v1/plugin.js";

function modeValue(mode: OpenMode): string {
  return mode === "incontext" ? "incontext" : "redirect";
}

function openOptions(mode: OpenMode, fields: string): string {
  return `mode: "${modeValue(mode)}",
      endpoint: "/api/verifications",
      ${fields}`;
}

export function clientSnippet(
  library: ClientLibrary,
  mode: OpenMode,
  buttonComment: string,
  buttonLabel: string,
): string {
  const literal = openOptions(
    mode,
    `externalId: "order-123",
      firstName: "Ada",
      lastName: "Lovelace",
      email: "ada@example.com",`,
  );
  const bound = openOptions(
    mode,
    `externalId,
      firstName,
      lastName,
      email,`,
  );

  switch (library) {
    case "javascript":
      return `<script src="${SCRIPT}" async></script>

<!-- ${buttonComment} -->
<button type="button" id="verify">${buttonLabel}</button>
<script>
  document.getElementById("verify").addEventListener("click", function () {
    RecognizMe.open({
      ${literal}
    }).catch(function (error) {
      console.error(error.message);
    });
  });
</script>`;
    case "react":
      return `// ${SCRIPT}
// ${buttonComment}
export function Verify({ externalId, firstName, lastName, email }) {
  function verify() {
    RecognizMe.open({
      ${bound}
    }).catch(function (error) {
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
// ${SCRIPT}
// ${buttonComment}
const props = defineProps(["externalId", "firstName", "lastName", "email"]);

function verify() {
  RecognizMe.open({
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
      return `// ${SCRIPT}
// ${buttonComment}
@Component({
  selector: "app-verify",
  template: \`
    <button type="button" (click)="verify()">${buttonLabel}</button>
  \`,
})
export class VerifyComponent {
  @Input() externalId?: string;
  @Input() firstName?: string;
  @Input() lastName?: string;
  @Input() email?: string;

  verify() {
    RecognizMe.open({
      mode: "${modeValue(mode)}",
      endpoint: "/api/verifications",
      externalId: this.externalId,
      firstName: this.firstName,
      lastName: this.lastName,
      email: this.email,
    }).catch((error) => console.error(error.message));
  }
}`;
    case "svelte":
      return `<script>
  // ${SCRIPT}
  // ${buttonComment}
  export let externalId;
  export let firstName;
  export let lastName;
  export let email;

  function verify() {
    RecognizMe.open({
      ${bound}
    }).catch((error) => console.error(error.message));
  }
</script>

<button type="button" on:click={verify}>${buttonLabel}</button>`;
  }
}
