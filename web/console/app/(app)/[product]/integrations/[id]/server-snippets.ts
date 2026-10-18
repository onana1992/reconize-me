export type ServerLanguage = "node" | "python" | "php" | "java" | "csharp" | "go";

export const SERVER_LANGUAGES: { id: ServerLanguage; label: string }[] = [
  { id: "node", label: "Node.js" },
  { id: "python", label: "Python" },
  { id: "php", label: "PHP" },
  { id: "java", label: "Java" },
  { id: "csharp", label: "C#" },
  { id: "go", label: "Go" },
];

function comment(language: ServerLanguage, text: string): string {
  const mark = language === "python" ? "#" : "//";
  return `${mark} ${text}`;
}

export function serverSnippet(
  language: ServerLanguage,
  apiBase: string,
  keyPrefix: string,
  note: string,
  optional: string,
): string {
  const head = comment(language, note);
  const opt = comment(language, optional);
  const url = `${apiBase}/v1/verifications`;

  switch (language) {
    case "node":
      return `${head}
const apiKey = process.env.RECOGNIZME_API_KEY; // ${keyPrefix}

app.post("/api/verifications", async (req, res) => {
  const { externalId, firstName, lastName, email } = req.body; ${opt}

  const response = await fetch("${url}", {
    method: "POST",
    headers: {
      Authorization: \`Bearer \${apiKey}\`,
      "Content-Type": "application/json",
      "Idempotency-Key": externalId ?? crypto.randomUUID(),
    },
    body: JSON.stringify({
      external_id: externalId,
      applicant: {
        first_name: firstName,
        last_name: lastName,
        email,
      },
    }),
  });

  const session = await response.json();
  res.json({ hosted_url: session.hosted_url });
});`;
    case "python":
      return `${head}
import os
import uuid
import requests

api_key = os.environ["RECOGNIZME_API_KEY"]  # ${keyPrefix}

@app.post("/api/verifications")
def create_verification():
    body = request.get_json(silent=True) or {}  ${opt}
    external_id = body.get("externalId")
    response = requests.post(
        "${url}",
        headers={
            "Authorization": f"Bearer {api_key}",
            "Content-Type": "application/json",
            "Idempotency-Key": external_id or str(uuid.uuid4()),
        },
        json={
            "external_id": external_id,
            "applicant": {
                "first_name": body.get("firstName"),
                "last_name": body.get("lastName"),
                "email": body.get("email"),
            },
        },
        timeout=10,
    )
    session = response.json()
    return {"hosted_url": session["hosted_url"]}`;
    case "php":
      return `<?php
${head}
$apiKey = getenv("RECOGNIZME_API_KEY"); // ${keyPrefix}
$body = json_decode(file_get_contents("php://input"), true) ?? []; ${opt}
$externalId = $body["externalId"] ?? null;

$payload = json_encode([
    "external_id" => $externalId,
    "applicant" => [
        "first_name" => $body["firstName"] ?? null,
        "last_name" => $body["lastName"] ?? null,
        "email" => $body["email"] ?? null,
    ],
]);

$ch = curl_init("${url}");
curl_setopt_array($ch, [
    CURLOPT_POST => true,
    CURLOPT_RETURNTRANSFER => true,
    CURLOPT_HTTPHEADER => [
        "Authorization: Bearer $apiKey",
        "Content-Type: application/json",
        "Idempotency-Key: " . ($externalId ?: bin2hex(random_bytes(16))),
    ],
    CURLOPT_POSTFIELDS => $payload,
]);
$session = json_decode(curl_exec($ch), true);
curl_close($ch);

header("Content-Type: application/json");
echo json_encode(["hosted_url" => $session["hosted_url"]]);`;
    case "java":
      return `${head}
String apiKey = System.getenv("RECOGNIZME_API_KEY"); // ${keyPrefix}

@PostMapping("/api/verifications")
Map<String, String> create(@RequestBody Map<String, Object> body) throws Exception {
    String externalId = (String) body.get("externalId"); ${opt}
    Map<String, Object> applicant = new LinkedHashMap<>();
    applicant.put("first_name", body.get("firstName"));
    applicant.put("last_name", body.get("lastName"));
    applicant.put("email", body.get("email"));
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("external_id", externalId);
    payload.put("applicant", applicant);

    HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("${url}"))
            .header("Authorization", "Bearer " + apiKey)
            .header("Content-Type", "application/json")
            .header("Idempotency-Key", externalId != null ? externalId : UUID.randomUUID().toString())
            .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
            .build();
    HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
    String hostedUrl = mapper.readTree(response.body()).path("hosted_url").asText();
    return Map.of("hosted_url", hostedUrl);
}`;
    case "csharp":
      return `${head}
var apiKey = Environment.GetEnvironmentVariable("RECOGNIZME_API_KEY"); // ${keyPrefix}

app.MapPost("/api/verifications", async (ApplicantRequest body, HttpClient http) =>
{
    var externalId = body.ExternalId; ${opt}
    var payload = new
    {
        external_id = externalId,
        applicant = new
        {
            first_name = body.FirstName,
            last_name = body.LastName,
            email = body.Email,
        },
    };
    var request = new HttpRequestMessage(HttpMethod.Post, "${url}");
    request.Headers.Authorization = new AuthenticationHeaderValue("Bearer", apiKey);
    request.Headers.TryAddWithoutValidation("Idempotency-Key", externalId ?? Guid.NewGuid().ToString());
    request.Content = JsonContent.Create(payload);
    var response = await http.SendAsync(request);
    var session = await response.Content.ReadFromJsonAsync<SessionResponse>();
    return Results.Json(new { hosted_url = session!.HostedUrl });
});`;
    case "go":
      return `${head}
apiKey := os.Getenv("RECOGNIZME_API_KEY") // ${keyPrefix}

http.HandleFunc("/api/verifications", func(w http.ResponseWriter, r *http.Request) {
	var body struct {
		ExternalID string \`json:"externalId"\` ${opt}
		FirstName  string \`json:"firstName"\`
		LastName   string \`json:"lastName"\`
		Email      string \`json:"email"\`
	}
	json.NewDecoder(r.Body).Decode(&body)
	idempotency := body.ExternalID
	if idempotency == "" {
		idempotency = uuid.NewString()
	}
	payload, _ := json.Marshal(map[string]any{
		"external_id": body.ExternalID,
		"applicant": map[string]string{
			"first_name": body.FirstName,
			"last_name":  body.LastName,
			"email":      body.Email,
		},
	})
	req, _ := http.NewRequest(http.MethodPost, "${url}", bytes.NewReader(payload))
	req.Header.Set("Authorization", "Bearer "+apiKey)
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Idempotency-Key", idempotency)
	res, _ := http.DefaultClient.Do(req)
	defer res.Body.Close()
	var session struct {
		HostedURL string \`json:"hosted_url"\`
	}
	json.NewDecoder(res.Body).Decode(&session)
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(map[string]string{"hosted_url": session.HostedURL})
})`;
  }
}
