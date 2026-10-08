package com.kyc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kyc.adapters.UnavailableVisionDocument;
import com.kyc.entities.ApiKey;
import com.kyc.entities.Integration;
import com.kyc.entities.Organization;
import com.kyc.enums.IntegrationMode;
import com.kyc.enums.ProductCode;
import com.kyc.repositories.ApiKeyRepository;
import com.kyc.repositories.IntegrationRepository;
import com.kyc.repositories.OrganizationRepository;
import com.kyc.services.ApiKeyAuthenticator;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = "kyc.document-ia.provider=unavailable")
class DocumentIaVisionUnavailableTest {

    private static final String LIVE = "ky_live_v4fail01";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private OrganizationRepository organizations;

    @Autowired
    private IntegrationRepository integrations;

    @Autowired
    private ApiKeyRepository apiKeys;

    @Autowired
    private UnavailableVisionDocument vision;

    @BeforeEach
    void seed() {
        vision.reset();
        Instant now = Instant.parse("2026-10-06T12:00:00Z");
        UUID organizationId = UUID.randomUUID();
        UUID integrationId = UUID.randomUUID();
        organizations.save(new Organization(organizationId, "Vision Fail", "vision-fail", now));
        integrations.save(new Integration(
                integrationId, organizationId, ProductCode.IDENTITY, IntegrationMode.LIVE, "LIVE", now));
        apiKeys.save(new ApiKey(
                UUID.randomUUID(),
                organizationId,
                integrationId,
                LIVE.substring(0, ApiKeyAuthenticator.PREFIX_LENGTH),
                passwordEncoder.encode(LIVE),
                now));
    }

    @Test
    void secondFailureStaysInTheEnvelope() throws Exception {
        mockMvc.perform(multipart("/v1/document-ia/analyze")
                        .file(sharpPng())
                        .param("until", "vision")
                        .header("Authorization", "Bearer " + LIVE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stoppedAt").value("vision"))
                .andExpect(jsonPath("$.providerCalled").value(true))
                .andExpect(jsonPath("$.quality.readable").value(true))
                .andExpect(jsonPath("$.rawModel").value(nullValue()))
                .andExpect(jsonPath("$.parsed").value(nullValue()))
                .andExpect(jsonPath("$.indicators[0]").value("provider_unavailable"))
                .andExpect(jsonPath("$.decision").value(nullValue()));
        assertEquals(2, vision.attempts());
    }

    private static MockMultipartFile sharpPng() throws Exception {
        int width = 640;
        int height = 480;
        int step = Math.max(1, (int) Math.round(Math.min(width, height) / 320.0));
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        int sampleWidth = Math.max(1, width / step);
        int sampleHeight = Math.max(1, height / step);
        for (int gy = 0; gy < sampleHeight; gy++) {
            for (int gx = 0; gx < sampleWidth; gx++) {
                image.setRGB(
                        Math.min(width - 1, gx * step),
                        Math.min(height - 1, gy * step),
                        ((gx + gy) % 2 == 0) ? 0x000000 : 0xFFFFFF);
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return new MockMultipartFile("file", "sharp.png", MediaType.IMAGE_PNG_VALUE, out.toByteArray());
    }
}
