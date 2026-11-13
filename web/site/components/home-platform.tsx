import Link from "next/link";
import { Band, BandHead } from "./band";
import { Reveal } from "./reveal";
import type { Locale } from "../lib/locales";
import type { Messages } from "../lib/messages";
import { href, type RouteKey } from "../lib/routes";

/*
 * La plateforme — grille de modules, trois mis en avant.
 */

const ROUTE_KEYS = new Set<string>([
  "identityVerification",
  "biometricAuthentication",
  "amlScreening",
]);

function isRouteKey(value: string): value is RouteKey {
  return ROUTE_KEYS.has(value);
}

export function HomePlatform({ locale, copy }: { locale: Locale; copy: Messages["home"] }) {
  return (
    <Band tone="paper" labelledBy="home-platform-title">
      <Reveal>
        <BandHead
          id="home-platform-title"
          title={copy.platformTitle}
          lead={copy.platformLead}
        />
      </Reveal>

      <ul className="rm-platform">
        {copy.platformModules.map((module, index) => {
          const route = isRouteKey(module.route) ? module.route : null;
          const inner = (
            <>
              <span className="rm-platform-index">{String(index + 1).padStart(2, "0")}</span>
              <h3>{module.title}</h3>
              <p>{module.body}</p>
              {route ? <span className="rm-platform-more">{copy.platformMore}</span> : null}
            </>
          );

          return (
            <li key={module.key}>
              <Reveal delay={index * 40}>
                {route ? (
                  <Link
                    className="rm-platform-card"
                    data-featured={module.featured ? "true" : "false"}
                    data-linked="true"
                    href={href(locale, route)}
                  >
                    {inner}
                  </Link>
                ) : (
                  <div
                    className="rm-platform-card"
                    data-featured={module.featured ? "true" : "false"}
                  >
                    {inner}
                  </div>
                )}
              </Reveal>
            </li>
          );
        })}
      </ul>
    </Band>
  );
}
