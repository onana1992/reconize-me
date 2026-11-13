"use client";

import { useState } from "react";
import { AbstractDocument, CaptureFrame, SignalList, Verdict } from "./proof";
import type { Messages } from "../lib/messages";

/*
 * Parcours utilisateur — étapes cliquables + téléphone.
 */

export function HomeJourney({
  copy,
  stateLabels,
  decisionLabels,
}: {
  copy: Messages["home"];
  stateLabels: Messages["signalStates"];
  decisionLabels: Messages["decisions"];
}) {
  const [active, setActive] = useState(0);
  const step = copy.journeySteps[active] ?? copy.journeySteps[0];

  return (
    <section className="rm-band" data-tone="stone" aria-labelledby="home-journey-title">
      <div className="rm-shell">
        <div className="rm-band-head">
          <h2 id="home-journey-title" className="rm-title">
            {copy.journeyTitle}
          </h2>
          <p className="rm-lead">{copy.journeyLead}</p>
        </div>

        <div className="rm-journey">
          <div className="rm-journey-steps" role="list" aria-label={copy.journeyStepsLabel}>
            {copy.journeySteps.map((item, index) => {
              const selected = index === active;
              return (
                <button
                  key={item.key}
                  type="button"
                  role="listitem"
                  className="rm-journey-step"
                  aria-current={selected ? "step" : undefined}
                  aria-pressed={selected}
                  onClick={() => setActive(index)}
                >
                  <span className="rm-journey-index">{String(index + 1).padStart(2, "0")}</span>
                  <span className="rm-journey-step-copy">
                    <span className="rm-journey-step-title">{item.title}</span>
                    <span className="rm-journey-step-body">{item.body}</span>
                  </span>
                </button>
              );
            })}
          </div>

          <figure className="rm-device rm-journey-device" aria-label={step.screenLabel}>
            <div className="rm-device-screen">
              <div className="rm-journey-screen" data-step={step.key}>
                {step.key === "capture" ? (
                  <>
                    <p className="rm-journey-screen-title">{step.screenTitle}</p>
                    <CaptureFrame>
                      <div className="rm-journey-blur" aria-hidden="true" />
                    </CaptureFrame>
                    <p className="rm-journey-screen-note">{step.screenNote}</p>
                  </>
                ) : null}

                {step.key === "document" ? (
                  <>
                    <p className="rm-journey-screen-title">{step.screenTitle}</p>
                    <AbstractDocument
                      kind={copy.journeyDocKind}
                      label={step.screenLabel}
                      fieldsLabel={copy.journeyFieldsLabel}
                    />
                    <p className="rm-journey-screen-note">{step.screenNote}</p>
                  </>
                ) : null}

                {step.key === "decision" ? (
                  <>
                    <SignalList
                      label={copy.journeySignalsLabel}
                      stateLabels={stateLabels}
                      items={[
                        { name: copy.journeySignalDocument, state: "pass" },
                        { name: copy.journeySignalFace, state: "pass" },
                        { name: copy.journeySignalPresence, state: "pass" },
                      ]}
                    />
                    <Verdict
                      state="approved"
                      stateLabel={decisionLabels.approved}
                      title={copy.journeyVerdict}
                      titleAs="p"
                      summary={step.screenNote}
                    />
                  </>
                ) : null}
              </div>
            </div>
          </figure>
        </div>
      </div>
    </section>
  );
}
