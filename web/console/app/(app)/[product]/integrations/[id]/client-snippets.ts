export type OpenMode = "redirect" | "incontext";

export type ClientLibrary = "javascript" | "react" | "vue" | "angular" | "svelte";

export const CLIENT_LIBRARIES: { id: ClientLibrary; label: string }[] = [
  { id: "javascript", label: "JavaScript" },
  { id: "react", label: "React" },
  { id: "vue", label: "Vue" },
  { id: "angular", label: "Angular" },
  { id: "svelte", label: "Svelte" },
];

const SCRIPT = "https://js.recogniz.me/v1/button.js";

function element(mode: OpenMode, bind: "html" | "react" | "vue" | "angular" | "svelte"): string {
  const modeValue = mode === "incontext" ? "incontext" : "redirect";
  if (bind === "html") {
    return `<recogniz-me-button
  mode="${modeValue}"
  endpoint="/api/verifications"
  external-id="order-123"
  first-name="Ada"
  last-name="Lovelace"
  email="ada@example.com"
></recogniz-me-button>`;
  }
  if (bind === "react" || bind === "svelte") {
    return `<recogniz-me-button
  mode="${modeValue}"
  endpoint="/api/verifications"
  external-id={externalId}
  first-name={firstName}
  last-name={lastName}
  email={email}
></recogniz-me-button>`;
  }
  if (bind === "vue") {
    return `<recogniz-me-button
  mode="${modeValue}"
  endpoint="/api/verifications"
  :external-id="externalId"
  :first-name="firstName"
  :last-name="lastName"
  :email="email"
></recogniz-me-button>`;
  }
  return `<recogniz-me-button
  mode="${modeValue}"
  endpoint="/api/verifications"
  [attr.external-id]="externalId"
  [attr.first-name]="firstName"
  [attr.last-name]="lastName"
  [attr.email]="email"
></recogniz-me-button>`;
}

export function clientSnippet(library: ClientLibrary, mode: OpenMode): string {
  switch (library) {
    case "javascript":
      return `<script src="${SCRIPT}" async></script>
${element(mode, "html")}`;
    case "react":
      return `import "${SCRIPT}";

export function Verify({ externalId, firstName, lastName, email }) {
  return (
    ${element(mode, "react").replaceAll("\n", "\n    ")}
  );
}`;
    case "vue":
      return `<script setup>
import "${SCRIPT}";

defineProps(["externalId", "firstName", "lastName", "email"]);
</script>

<template>
  ${element(mode, "vue").replaceAll("\n", "\n  ")}
</template>`;
    case "angular":
      return `// ${SCRIPT}
@Component({
  selector: "app-verify",
  schemas: [CUSTOM_ELEMENTS_SCHEMA],
  template: \`
    ${element(mode, "angular").replaceAll("\n", "\n    ")}
  \`,
})
export class VerifyComponent {
  @Input() externalId?: string;
  @Input() firstName?: string;
  @Input() lastName?: string;
  @Input() email?: string;
}`;
    case "svelte":
      return `<script>
  import "${SCRIPT}";

  export let externalId;
  export let firstName;
  export let lastName;
  export let email;
</script>

${element(mode, "svelte")}`;
  }
}
