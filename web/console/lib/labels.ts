export function roleLabel(role: string): string {
  if (role === "owner") {
    return "Propriétaire";
  }
  if (role === "member") {
    return "Membre";
  }
  return role;
}
