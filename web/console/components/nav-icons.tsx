type IconName =
  | "home"
  | "idcard"
  | "scan"
  | "search"
  | "key"
  | "team"
  | "user"
  | "menu"
  | "close"
  | "collapse"
  | "expand"
  | "logout";

const PATHS: Record<IconName, string> = {
  home: "M2.5 7.2 8 2.8l5.5 4.4V13a1 1 0 0 1-1 1H3.5a1 1 0 0 1-1-1V7.2Z M6.2 14v-4.2h3.6V14",
  idcard: "M2.6 4.2h10.8A1.2 1.2 0 0 1 14.6 5.4v6.2a1.2 1.2 0 0 1-1.2 1.2H2.6A1.2 1.2 0 0 1 1.4 11.6V5.4A1.2 1.2 0 0 1 2.6 4.2ZM4.2 6.4h2.4v2.6H4.2ZM8.4 6.6h4M8.4 9.2h3",
  scan: "M3 5.2V3.2h2M11 3.2h2v2M3 10.8v2h2M13 10.8v2h-2M8 8.8A2 2 0 1 0 8 4.8a2 2 0 0 0 0 4ZM5.6 12.4c.6-1.1 1.6-1.6 2.4-1.6s1.8.5 2.4 1.6",
  search: "M7 10.4A3.4 3.4 0 1 1 7 3.6a3.4 3.4 0 0 1 0 6.8ZM9.5 9.5 13.2 13.2",
  key: "M9.6 7.2a3.2 3.2 0 1 0-2.3 2.3L10.8 13l1.6-1.6-1.1-1.1 1.1-1.1Z",
  team: "M5.2 7.2a1.8 1.8 0 1 0 0-3.6 1.8 1.8 0 0 0 0 3.6ZM10.8 7.2a1.8 1.8 0 1 0 0-3.6 1.8 1.8 0 0 0 0 3.6ZM2.8 13v-.6A2.6 2.6 0 0 1 5.4 9.8h.4M13.2 13v-.6A2.6 2.6 0 0 0 10.6 9.8h-.4M6.2 13v-.8A2.6 2.6 0 0 1 8.8 9.6h.4",
  user: "M8 8.2A2.4 2.4 0 1 0 8 3.4a2.4 2.4 0 0 0 0 4.8ZM3.4 13.2c.4-2.2 2.2-3.6 4.6-3.6s4.2 1.4 4.6 3.6",
  menu: "M2.8 4.2h10.4M2.8 8h10.4M2.8 11.8h10.4",
  close: "M4 4l8 8M12 4l-8 8",
  collapse:
    "M3.4 2.8h9.2A1.2 1.2 0 0 1 13.8 4v8a1.2 1.2 0 0 1-1.2 1.2H3.4A1.2 1.2 0 0 1 2.2 12V4a1.2 1.2 0 0 1 1.2-1.2ZM6.4 2.8v10.4M11.1 10 9 8l2.1-2",
  expand:
    "M3.4 2.8h9.2A1.2 1.2 0 0 1 13.8 4v8a1.2 1.2 0 0 1-1.2 1.2H3.4A1.2 1.2 0 0 1 2.2 12V4a1.2 1.2 0 0 1 1.2-1.2ZM6.4 2.8v10.4M9 6l2.1 2L9 10",
  logout: "M6.2 3.2H4.2a1 1 0 0 0-1 1v7.6a1 1 0 0 0 1 1h2M9 11.2 12.8 8 9 4.8M12.8 8H6.2",
};

export function NavIcon({ name }: { name: IconName }) {
  return (
    <svg className="rm-shell-glyph" width="16" height="16" viewBox="0 0 16 16" aria-hidden="true">
      <path
        d={PATHS[name]}
        fill="none"
        stroke="currentColor"
        strokeWidth="1.4"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}

export type { IconName };
