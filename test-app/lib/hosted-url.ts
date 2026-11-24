const LOCAL_FLOW_ORIGIN = "http://localhost:3001";

/** Ouvre le flow local dans l’iframe du test-app. */
export function rewriteHostedUrl(hostedUrl: string): string {
  try {
    const incoming = new URL(hostedUrl);
    if (!incoming.pathname.startsWith("/flow/")) {
      return hostedUrl;
    }
    const target = new URL(LOCAL_FLOW_ORIGIN);
    incoming.protocol = target.protocol;
    incoming.host = target.host;
    return incoming.toString();
  } catch {
    return hostedUrl;
  }
}
