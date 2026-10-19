import type { ServerLanguage } from "../server-snippets";

function comment(language: ServerLanguage, text: string): string {
  const mark = language === "python" ? "#" : "//";
  return `${mark} ${text}`;
}

export function webhookSnippet(language: ServerLanguage, note: string): string {
  const head = comment(language, note);

  switch (language) {
    case "node":
      return `${head}
const crypto = require("crypto");
const secret = process.env.RECOGNIZME_WEBHOOK_SECRET;

app.post("/hooks/idv", express.raw({ type: "application/json" }), (req, res) => {
  const header = req.get("X-RecognizMe-Signature") || "";
  const match = /^t=(\\d+),v1=([0-9a-f]+)$/.exec(header);
  if (!match) return res.sendStatus(401);

  const raw = req.body.toString("utf8");
  const expected = crypto.createHmac("sha256", secret).update(match[1] + "." + raw).digest("hex");
  const valid =
    expected.length === match[2].length &&
    crypto.timingSafeEqual(Buffer.from(expected), Buffer.from(match[2]));
  if (!valid) return res.sendStatus(401);

  const event = JSON.parse(raw);
  const decision = event.data.decision;
  const externalId = event.data.external_id;
  res.sendStatus(200);
});`;
    case "python":
      return `${head}
import hashlib
import hmac
import os

secret = os.environ["RECOGNIZME_WEBHOOK_SECRET"].encode()

@app.post("/hooks/idv")
def hook():
    header = request.headers.get("X-RecognizMe-Signature", "")
    parts = header.split(",v1=")
    if len(parts) != 2 or not parts[0].startswith("t="):
        return "", 401
    timestamp = parts[0].removeprefix("t=")
    raw = request.get_data()
    expected = hmac.new(secret, f"{timestamp}.".encode() + raw, hashlib.sha256).hexdigest()
    if not hmac.compare_digest(expected, parts[1]):
        return "", 401
    event = request.get_json()
    decision = event["data"]["decision"]
    external_id = event["data"]["external_id"]
    return "", 200`;
    case "php":
      return `<?php
${head}
$secret = getenv("RECOGNIZME_WEBHOOK_SECRET");
$raw = file_get_contents("php://input");
$header = $_SERVER["HTTP_X_RECOGNIZME_SIGNATURE"] ?? "";

if (!preg_match('/^t=(\\d+),v1=([0-9a-f]+)$/', $header, $match)) {
    http_response_code(401);
    exit;
}
$expected = hash_hmac("sha256", $match[1] . "." . $raw, $secret);
if (!hash_equals($expected, $match[2])) {
    http_response_code(401);
    exit;
}
$event = json_decode($raw, true);
$decision = $event["data"]["decision"];
$externalId = $event["data"]["external_id"];
http_response_code(200);`;
    case "java":
      return `${head}
String secret = System.getenv("RECOGNIZME_WEBHOOK_SECRET");

@PostMapping("/hooks/idv")
ResponseEntity<Void> hook(HttpServletRequest request) throws Exception {
    String raw = new String(request.getInputStream().readAllBytes(), UTF_8);
    String header = Objects.toString(request.getHeader("X-RecognizMe-Signature"), "");
    Matcher match = Pattern.compile("^t=(\\\\d+),v1=([0-9a-f]+)$").matcher(header);
    if (!match.matches()) return ResponseEntity.status(401).build();

    String payload = match.group(1) + "." + raw;
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(secret.getBytes(UTF_8), "HmacSHA256"));
    String expected = HexFormat.of().formatHex(mac.doFinal(payload.getBytes(UTF_8)));
    byte[] actual = match.group(2).getBytes(UTF_8);
    if (!MessageDigest.isEqual(expected.getBytes(UTF_8), actual)) {
        return ResponseEntity.status(401).build();
    }
    JsonNode event = mapper.readTree(raw);
    String decision = event.path("data").path("decision").asText();
    String externalId = event.path("data").path("external_id").asText();
    return ResponseEntity.ok().build();
}`;
    case "csharp":
      return `${head}
var secret = Environment.GetEnvironmentVariable("RECOGNIZME_WEBHOOK_SECRET")!;

app.MapPost("/hooks/idv", async (HttpRequest request) =>
{
    using var reader = new StreamReader(request.Body);
    var raw = await reader.ReadToEndAsync();
    var header = request.Headers["X-RecognizMe-Signature"].ToString();
    var match = Regex.Match(header, @"^t=(\\d+),v1=([0-9a-f]+)$");
    if (!match.Success) return Results.StatusCode(401);

    var payload = match.Groups[1].Value + "." + raw;
    var hmac = new HMACSHA256(Encoding.UTF8.GetBytes(secret));
    var expected = Convert.ToHexString(hmac.ComputeHash(Encoding.UTF8.GetBytes(payload))).ToLowerInvariant();
    var actual = Encoding.UTF8.GetBytes(match.Groups[2].Value);
    if (expected.Length != match.Groups[2].Value.Length ||
        !CryptographicOperations.FixedTimeEquals(Encoding.UTF8.GetBytes(expected), actual))
        return Results.StatusCode(401);

    var body = JsonNode.Parse(raw)!;
    var decision = body["data"]!["decision"]!.GetValue<string>();
    var externalId = body["data"]!["external_id"]?.GetValue<string>();
    return Results.Ok();
});`;
    case "go":
      return `${head}
secret := os.Getenv("RECOGNIZME_WEBHOOK_SECRET")

http.HandleFunc("/hooks/idv", func(w http.ResponseWriter, r *http.Request) {
	raw, _ := io.ReadAll(r.Body)
	header := r.Header.Get("X-RecognizMe-Signature")
	parts := strings.Split(header, ",v1=")
	if len(parts) != 2 || !strings.HasPrefix(parts[0], "t=") {
		w.WriteHeader(http.StatusUnauthorized)
		return
	}
	timestamp := strings.TrimPrefix(parts[0], "t=")
	mac := hmac.New(sha256.New, []byte(secret))
	mac.Write([]byte(timestamp + "."))
	mac.Write(raw)
	expected := hex.EncodeToString(mac.Sum(nil))
	if !hmac.Equal([]byte(expected), []byte(parts[1])) {
		w.WriteHeader(http.StatusUnauthorized)
		return
	}
	var event struct {
		Data struct {
			Decision   string \`json:"decision"\`
			ExternalID string \`json:"external_id"\`
		} \`json:"data"\`
	}
	json.Unmarshal(raw, &event)
	w.WriteHeader(http.StatusOK)
})`;
  }
}
