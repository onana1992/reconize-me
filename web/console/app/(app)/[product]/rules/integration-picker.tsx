"use client";

import { useRouter } from "next/navigation";
import type { IntegrationListItem } from "../../../../lib/api";

export function ProductionIntegrationPicker({
  product,
  integrations,
  selectedId,
  label,
}: {
  product: string;
  integrations: IntegrationListItem[];
  selectedId: string;
  label: string;
}) {
  const router = useRouter();
  return (
    <label className="rm-rules-integration">
      <span>{label}</span>
      <select
        value={selectedId}
        onChange={(event) => {
          router.push(`/${product}/rules?integration=${encodeURIComponent(event.target.value)}`);
        }}
      >
        {integrations.map((item) => (
          <option key={item.id} value={item.id}>
            {item.name}
          </option>
        ))}
      </select>
    </label>
  );
}
