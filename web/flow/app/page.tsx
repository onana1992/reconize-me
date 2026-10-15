import { FlowShell, StateScreen } from "../components/flow-shell";
import { LinkOffIcon } from "../components/icons";
import { getT } from "../i18n";

export default async function Home() {
  const t = await getT();
  return (
    <FlowShell step={null}>
      <StateScreen tone="neutral" icon={<LinkOffIcon size={30} />} title={t("home.title")} lead={t("home.lead")} />
    </FlowShell>
  );
}
