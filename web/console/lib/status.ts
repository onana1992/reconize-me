export function formatUtc(iso: string, locale = "fr"): string {
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) {
    return iso;
  }
  return (
    new Intl.DateTimeFormat(locale, {
      dateStyle: "short",
      timeStyle: "medium",
      timeZone: "UTC",
    }).format(date) + " UTC"
  );
}
