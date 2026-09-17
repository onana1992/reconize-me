import { FlowBrand } from "../components/flow-brand";
import { getT } from "../i18n";

export default async function Home() {
  const t = await getT();
  return (
    <main className="rm-flow">
      <FlowBrand />
      <h1>{t("home.title")}</h1>
      <p className="rm-lead">{t("home.lead")}</p>
    </main>
  );
}
