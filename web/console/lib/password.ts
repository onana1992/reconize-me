export const NEW_PASSWORD_ATTRS = {
  minLength: 12,
  maxLength: 128,
  pattern: "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{12,128}$",
} as const;
