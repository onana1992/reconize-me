# Hosted flow (M4)

Applicant journey on port **3001**. No organization name, no decision, no extracted identity.

Sandbox scenarios are chosen at session creation (`metadata.sandbox_scenario`), not by the filename of a photo.

Phone camera with in-page frame needs a **trusted HTTPS** origin. A self-signed cert on `https://10.0.0.133` is usually blocked. Run the flow (`npm run dev:flow`), then in another terminal `npm run dev:flow:tunnel` and open the `https://*.trycloudflare.com/flow/...` URL on the phone.
