"use client";

import { useEffect, useRef } from "react";

type Props = {
  mode: "redirect" | "incontext";
  externalId: string;
  firstName: string;
  lastName: string;
  email: string;
  onError: (message: string) => void;
};

export function VerifyButton({ mode, externalId, firstName, lastName, email, onError }: Props) {
  const host = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const parent = host.current;
    if (!parent) return;
    const element = parent.querySelector("recogniz-me-button") ?? document.createElement("recogniz-me-button");
    element.setAttribute("mode", mode);
    element.setAttribute("endpoint", "/api/verifications");
    element.setAttribute("lang", "fr");
    setOptional(element, "external-id", externalId);
    setOptional(element, "first-name", firstName);
    setOptional(element, "last-name", lastName);
    setOptional(element, "email", email);
    if (!element.isConnected) parent.appendChild(element);

    const report = (event: Event) => {
      const detail = (event as CustomEvent<string>).detail;
      onError(typeof detail === "string" ? detail : "La session n’a pas pu être créée.");
    };
    element.addEventListener("recogniz-error", report);
    return () => element.removeEventListener("recogniz-error", report);
  }, [mode, externalId, firstName, lastName, email, onError]);

  return <div ref={host} />;
}

function setOptional(element: Element, name: string, value: string) {
  const trimmed = value.trim();
  if (trimmed) element.setAttribute(name, trimmed);
  else element.removeAttribute(name);
}
