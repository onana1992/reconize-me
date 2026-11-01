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

const FAILURE = '{"error":{"message":"API unreachable"}}';

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
  const failure = JSON.stringify(FAILURE);

  switch (language) {
    case "node":
      return `${head}
const crypto = require("crypto");
const express = require("express");

const app = express();
app.use(express.json());

const apiKey = process.env.RECOGNIZME_API_KEY; // ${keyPrefix}

function idempotencyKey(externalId) {
  const usable = typeof externalId === "string"
    && externalId.length >= 8
    && externalId.length <= 64
    && [...externalId].every((ch) => {
      const code = ch.charCodeAt(0);
      return code >= 32 && code <= 126;
    });
  return usable ? externalId : crypto.randomUUID();
}

app.post("/api/verifications", async (req, res) => {
  const body = req.body ?? {}; ${opt}
  const { externalId, firstName, lastName, email } = body;

  let response;
  try {
    response = await fetch("${url}", {
      method: "POST",
      headers: {
        Authorization: \`Bearer \${apiKey}\`,
        "Content-Type": "application/json",
        "Idempotency-Key": idempotencyKey(externalId),
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
  } catch {
    res.status(502).type("json").send(${failure});
    return;
  }

  const session = await response.json().catch(() => ({}));
  if (!response.ok || !session.hosted_url) {
    res.status(response.ok ? 502 : response.status).json(session);
    return;
  }
  res.json({ hosted_url: session.hosted_url });
});

app.listen(3000);`;
    case "python":
      return `${head}
import os
import uuid

import requests
from flask import Flask, request

app = Flask(__name__)
api_key = os.environ["RECOGNIZME_API_KEY"]  # ${keyPrefix}

def idempotency_key(external_id):
    if (
        isinstance(external_id, str)
        and 8 <= len(external_id) <= 64
        and all(32 <= ord(ch) <= 126 for ch in external_id)
    ):
        return external_id
    return str(uuid.uuid4())

@app.post("/api/verifications")
def create_verification():
    body = request.get_json(silent=True) or {}  ${opt}
    external_id = body.get("externalId")
    try:
        response = requests.post(
            "${url}",
            headers={
                "Authorization": f"Bearer {api_key}",
                "Content-Type": "application/json",
                "Idempotency-Key": idempotency_key(external_id),
            },
            json={
                "external_id": external_id,
                "applicant": {
                    "first_name": body.get("firstName"),
                    "last_name": body.get("lastName"),
                    "email": body.get("email"),
                },
            },
            timeout=30,
        )
        session = response.json()
    except (requests.RequestException, ValueError):
        return {"error": {"message": "API unreachable"}}, 502
    if response.status_code >= 400 or not session.get("hosted_url"):
        return session, response.status_code if response.status_code >= 400 else 502
    return {"hosted_url": session["hosted_url"]}

if __name__ == "__main__":
    app.run(port=3000)`;
    case "php":
      return `<?php
${head}
$apiKey = getenv("RECOGNIZME_API_KEY"); // ${keyPrefix}
$body = json_decode(file_get_contents("php://input"), true) ?? []; ${opt}
$externalId = $body["externalId"] ?? null;

function idempotency_key($externalId) {
    $usable = is_string($externalId)
        && strlen($externalId) >= 8
        && strlen($externalId) <= 64
        && preg_match('/^[ -~]{8,64}$/', $externalId) === 1;
    return $usable ? $externalId : bin2hex(random_bytes(16));
}

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
        "Idempotency-Key: " . idempotency_key($externalId),
    ],
    CURLOPT_POSTFIELDS => $payload,
]);
$raw = curl_exec($ch);
$status = curl_getinfo($ch, CURLINFO_RESPONSE_CODE);
$failed = $raw === false;
curl_close($ch);

header("Content-Type: application/json");
$session = is_string($raw) ? json_decode($raw, true) : null;
if ($failed || !is_array($session) || $status >= 400 || empty($session["hosted_url"])) {
    http_response_code($status >= 400 ? $status : 502);
    echo json_encode(is_array($session) ? $session : ["error" => ["message" => "API unreachable"]]);
    exit;
}
http_response_code(200);
echo json_encode(["hosted_url" => $session["hosted_url"]]);`;
    case "java":
      return `${head}
// Déposez cette classe dans le package scanné par votre application.
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
class VerificationProxy {
    private static final String API_KEY = System.getenv("RECOGNIZME_API_KEY"); // ${keyPrefix}
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();

    private static String idempotencyKey(String externalId) {
        boolean usable = externalId != null
                && externalId.length() >= 8
                && externalId.length() <= 64
                && externalId.chars().allMatch(ch -> ch >= 32 && ch <= 126);
        return usable ? externalId : UUID.randomUUID().toString();
    }

    @PostMapping("/api/verifications")
    ResponseEntity<String> create(@RequestBody Map<String, Object> body) {
        Object external = body.get("externalId"); ${opt}
        String externalId = external instanceof String value ? value : null;
        Map<String, Object> applicant = new LinkedHashMap<>();
        applicant.put("first_name", body.get("firstName"));
        applicant.put("last_name", body.get("lastName"));
        applicant.put("email", body.get("email"));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("external_id", externalId);
        payload.put("applicant", applicant);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("${url}"))
                    .header("Authorization", "Bearer " + API_KEY)
                    .header("Content-Type", "application/json")
                    .header("Idempotency-Key", idempotencyKey(externalId))
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            String hostedUrl = mapper.readTree(response.body()).path("hosted_url").asText("");
            if (response.statusCode() >= 400 || hostedUrl.isBlank()) {
                int status = response.statusCode() >= 400 ? response.statusCode() : 502;
                return ResponseEntity.status(status)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(response.body());
            }
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(mapper.writeValueAsString(Map.of("hosted_url", hostedUrl)));
        } catch (IOException | InterruptedException ex) {
            return ResponseEntity.status(502)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(${failure});
        }
    }
}`;
    case "csharp":
      return `${head}
using System.Linq;
using System.Net.Http.Headers;
using System.Net.Http.Json;
using System.Text.Json.Serialization;

var builder = WebApplication.CreateBuilder(args);
var app = builder.Build();
var apiKey = Environment.GetEnvironmentVariable("RECOGNIZME_API_KEY"); // ${keyPrefix}

static string IdempotencyKey(string? externalId) =>
    externalId is { Length: >= 8 and <= 64 } && externalId.All(ch => ch >= ' ' && ch <= '~')
        ? externalId
        : Guid.NewGuid().ToString();

app.MapPost("/api/verifications", async (ApplicantRequest body) =>
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
    using var http = new HttpClient();
    try
    {
        var request = new HttpRequestMessage(HttpMethod.Post, "${url}");
        request.Headers.Authorization = new AuthenticationHeaderValue("Bearer", apiKey);
        request.Headers.TryAddWithoutValidation("Idempotency-Key", IdempotencyKey(externalId));
        request.Content = JsonContent.Create(payload);
        var response = await http.SendAsync(request);
        if (!response.IsSuccessStatusCode)
        {
            var error = await response.Content.ReadAsStringAsync();
            return Results.Content(error, "application/json", statusCode: (int)response.StatusCode);
        }
        var session = await response.Content.ReadFromJsonAsync<SessionResponse>();
        if (string.IsNullOrEmpty(session?.HostedUrl))
            return Results.Json(new { error = new { message = "API unreachable" } }, statusCode: 502);
        return Results.Json(new { hosted_url = session.HostedUrl });
    }
    catch (HttpRequestException)
    {
        return Results.Json(new { error = new { message = "API unreachable" } }, statusCode: 502);
    }
});

app.Run();

record ApplicantRequest(
    [property: JsonPropertyName("externalId")] string? ExternalId,
    [property: JsonPropertyName("firstName")] string? FirstName,
    [property: JsonPropertyName("lastName")] string? LastName,
    [property: JsonPropertyName("email")] string? Email);

record SessionResponse([property: JsonPropertyName("hosted_url")] string? HostedUrl);`;
    case "go":
      return `${head}
package main

import (
	"bytes"
	"crypto/rand"
	"encoding/hex"
	"encoding/json"
	"io"
	"net/http"
	"os"
)

func main() {
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
		if !validIdempotency(idempotency) {
			idempotency = newIdempotencyKey()
		}
		payload, _ := json.Marshal(map[string]any{
			"external_id": body.ExternalID,
			"applicant": map[string]string{
				"first_name": body.FirstName,
				"last_name":  body.LastName,
				"email":      body.Email,
			},
		})
		req, err := http.NewRequest(http.MethodPost, "${url}", bytes.NewReader(payload))
		if err != nil {
			writeJSON(w, http.StatusBadGateway, map[string]any{"error": map[string]string{"message": "API unreachable"}})
			return
		}
		req.Header.Set("Authorization", "Bearer "+apiKey)
		req.Header.Set("Content-Type", "application/json")
		req.Header.Set("Idempotency-Key", idempotency)
		res, err := http.DefaultClient.Do(req)
		if err != nil {
			writeJSON(w, http.StatusBadGateway, map[string]any{"error": map[string]string{"message": "API unreachable"}})
			return
		}
		defer res.Body.Close()
		raw, _ := io.ReadAll(res.Body)
		var session map[string]any
		json.Unmarshal(raw, &session)
		hosted, _ := session["hosted_url"].(string)
		if res.StatusCode >= 400 || hosted == "" {
			status := res.StatusCode
			if status < 400 {
				status = http.StatusBadGateway
			}
			w.Header().Set("Content-Type", "application/json")
			w.WriteHeader(status)
			w.Write(raw)
			return
		}
		writeJSON(w, http.StatusOK, map[string]string{"hosted_url": hosted})
	})
	http.ListenAndServe(":3000", nil)
}

func validIdempotency(value string) bool {
	if len(value) < 8 || len(value) > 64 {
		return false
	}
	for _, ch := range value {
		if ch < 32 || ch > 126 {
			return false
		}
	}
	return true
}

func newIdempotencyKey() string {
	buf := make([]byte, 16)
	_, _ = rand.Read(buf)
	return hex.EncodeToString(buf)
}

func writeJSON(w http.ResponseWriter, status int, body any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	json.NewEncoder(w).Encode(body)
}`;
  }
}
