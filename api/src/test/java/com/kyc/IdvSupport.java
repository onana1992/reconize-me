package com.kyc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kyc.entities.ApiKey;
import com.kyc.entities.Organization;
import com.kyc.repositories.ApiKeyRepository;
import com.kyc.repositories.OrganizationRepository;
import com.kyc.services.ApiKeyAuthenticator;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import javax.imageio.ImageIO;
import jakarta.servlet.http.Cookie;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

final class IdvSupport {

    static final ObjectMapper JSON = new ObjectMapper();

    private IdvSupport() {}

    static String seedBearer(
            OrganizationRepository organizations,
            ApiKeyRepository keys,
            PasswordEncoder encoder,
            String rawKey,
            String orgName,
            String slug) {
        Instant now = Instant.parse("2026-09-10T12:00:00Z");
        UUID organizationId = UUID.randomUUID();
        organizations.save(new Organization(organizationId, orgName, slug, now));
        keys.save(new ApiKey(
                UUID.randomUUID(),
                organizationId,
                rawKey.substring(0, ApiKeyAuthenticator.PREFIX_LENGTH),
                encoder.encode(rawKey),
                now));
        return rawKey;
    }

    static JsonNode create(MockMvc mockMvc, String rawKey, String body) throws Exception {
        MvcResult result = mockMvc.perform(post("/v1/verifications")
                        .header("Authorization", "Bearer " + rawKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body == null ? "{}" : body))
                .andExpect(status().isCreated())
                .andReturn();
        return JSON.readTree(result.getResponse().getContentAsString());
    }

    static JsonNode createConsole(MockMvc mockMvc, Cookie cookie, String body) throws Exception {
        MvcResult result = mockMvc.perform(post("/v1/console/verifications")
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body == null ? "{}" : body))
                .andExpect(status().isCreated())
                .andReturn();
        return JSON.readTree(result.getResponse().getContentAsString());
    }

    static String token(JsonNode created) {
        String url = created.path("hosted_url").asText();
        int slash = url.lastIndexOf('/');
        return url.substring(slash + 1);
    }

    static JsonNode flow(MockMvc mockMvc, String token) throws Exception {
        MvcResult result = mockMvc.perform(get("/v1/flow/" + token)).andExpect(status().isOk()).andReturn();
        return JSON.readTree(result.getResponse().getContentAsString());
    }

    static void acceptConsent(MockMvc mockMvc, String token) throws Exception {
        mockMvc.perform(post("/v1/flow/" + token + "/consent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"accepted\"}"))
                .andExpect(status().isCreated());
    }

    static void putUpload(MockMvc mockMvc, String uploadUrl, String objectKey, byte[] body) throws Exception {
        URI uri = URI.create(uploadUrl);
        String exp = null;
        String sig = null;
        String method = "PUT";
        String query = uri.getQuery();
        if (query != null) {
            for (String pair : query.split("&")) {
                int eq = pair.indexOf('=');
                if (eq < 1) {
                    continue;
                }
                String name = pair.substring(0, eq);
                String value = pair.substring(eq + 1);
                if ("exp".equals(name)) {
                    exp = value;
                } else if ("sig".equals(name)) {
                    sig = value;
                } else if ("method".equals(name)) {
                    method = value;
                }
            }
        }
        mockMvc.perform(put("/v1/objects")
                        .param("key", objectKey)
                        .param("exp", exp)
                        .param("sig", sig)
                        .param("method", method)
                        .contentType(MediaType.IMAGE_JPEG)
                        .content(body))
                .andExpect(status().isNoContent());
    }

    static JsonNode captureDocument(MockMvc mockMvc, String token, byte[] jpeg) throws Exception {
        MvcResult upload = mockMvc.perform(post("/v1/flow/" + token + "/document/uploads"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = JSON.readTree(upload.getResponse().getContentAsString());
        putUpload(mockMvc, body.path("upload_url").asText(), body.path("object_key").asText(), jpeg);
        MvcResult complete = mockMvc.perform(post("/v1/flow/" + token + "/document/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"attempt\":" + body.path("attempt").asInt() + "}"))
                .andExpect(status().isOk())
                .andReturn();
        return JSON.readTree(complete.getResponse().getContentAsString());
    }

    static JsonNode captureSelfie(MockMvc mockMvc, String token, byte[] jpeg) throws Exception {
        MvcResult upload = mockMvc.perform(post("/v1/flow/" + token + "/selfie/uploads"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = JSON.readTree(upload.getResponse().getContentAsString());
        putUpload(mockMvc, body.path("upload_url").asText(), body.path("object_key").asText(), jpeg);
        MvcResult complete = mockMvc.perform(post("/v1/flow/" + token + "/selfie/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"attempt\":" + body.path("attempt").asInt() + "}"))
                .andExpect(status().isOk())
                .andReturn();
        return JSON.readTree(complete.getResponse().getContentAsString());
    }

    static byte[] jpeg(int size) throws Exception {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, size, size);
        graphics.setColor(Color.DARK_GRAY);
        graphics.fillRect(40, 40, size - 80, size - 80);
        graphics.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", out);
        return out.toByteArray();
    }

    static byte[] goodJpeg() throws Exception {
        return jpeg(800);
    }

    static byte[] tinyJpeg() throws Exception {
        return jpeg(64);
    }
}
