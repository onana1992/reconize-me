import { API_URL } from "../lib/site-config";

/*
 * Le panneau d'appel — section développeurs.
 *
 * Les exemples ne sont pas du contenu traduisible : ils vivent ici, pas dans
 * les dictionnaires. Ils sont écrits à partir des contrôleurs réels de l'API,
 * mêmes en-têtes et mêmes noms de champs, pour qu'un copier-coller marche.
 *
 * La coloration est posée à la main sur trois teintes. Embarquer un moteur de
 * coloration pour dix lignes coûterait plus de kilo-octets que toute la page,
 * et un extrait qui clignote en six couleurs ne se lit pas mieux.
 */

/** Une clé JSON et sa valeur en chaîne, sur une ligne. */
function Field({ name, value, last = false }: { name: string; value: string; last?: boolean }) {
  return (
    <>
      {"  "}
      <span className="rm-tok-key">&quot;{name}&quot;</span>
      <span className="rm-tok-mute">: </span>
      <span className="rm-tok-str">&quot;{value}&quot;</span>
      {last ? null : <span className="rm-tok-mute">,</span>}
      {"\n"}
    </>
  );
}

export function CodePanel({
  requestLabel,
  responseLabel,
}: {
  requestLabel: string;
  responseLabel: string;
}) {
  return (
    <div className="rm-console">
      <div className="rm-console-bar">
        <span className="rm-console-method">POST</span>
        <p className="rm-console-label">{requestLabel}</p>
      </div>

      <pre>
        <code>
          <span className="rm-tok-mute">curl -X POST </span>
          {API_URL}/v1/verifications <span className="rm-tok-mute">\</span>
          {"\n"}
          {"  "}
          <span className="rm-tok-mute">-H </span>
          <span className="rm-tok-str">
            &quot;Authorization: Bearer ky_test_…&quot;
          </span>{" "}
          <span className="rm-tok-mute">\</span>
          {"\n"}
          {"  "}
          <span className="rm-tok-mute">-H </span>
          <span className="rm-tok-str">&quot;Idempotency-Key: order-4821&quot;</span>{" "}
          <span className="rm-tok-mute">\</span>
          {"\n"}
          {"  "}
          <span className="rm-tok-mute">-d </span>
          <span className="rm-tok-str">
            &#39;&#123;&quot;external_id&quot;: &quot;order-4821&quot;&#125;&#39;
          </span>
        </code>
      </pre>

      <div className="rm-console-bar">
        <span className="rm-console-method">201</span>
        <p className="rm-console-label">{responseLabel}</p>
      </div>

      <pre>
        <code>
          <span className="rm-tok-mute">{"{\n"}</span>
          <Field name="id" value="9f2b6c14-3d5a-4e8b-9a71-2c0d5e7f8a13" />
          <Field name="status" value="created" />
          <Field name="hosted_url" value="https://flow.recogniz.me/flow/8Xk2…" />
          <Field name="expires_at" value="2026-09-02T13:05:00Z" last />
          <span className="rm-tok-mute">{"}"}</span>
        </code>
      </pre>
    </div>
  );
}
