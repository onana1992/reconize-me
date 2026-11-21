package com.kyc;

import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kyc.authenticity.SpecimenCache;
import com.kyc.entities.ApiKey;
import com.kyc.entities.Integration;
import com.kyc.entities.Organization;
import com.kyc.enums.IntegrationMode;
import com.kyc.enums.ProductCode;
import com.kyc.repositories.ApiKeyRepository;
import com.kyc.repositories.IntegrationRepository;
import com.kyc.repositories.OrganizationRepository;
import com.kyc.repositories.VerificationRepository;
import com.kyc.services.ApiKeyAuthenticator;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthenticityLabTest {

    private static final String LIVE = "ky_live_a0auth01";
    private static final String TEST = "ky_test_a0auth01";
    private static final String DOCUMENT = "QUEBEC_DRIVER_LICENSE:2024:FRONT";

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
    private VerificationRepository verifications;

    @BeforeEach
    void seed() {
        seed(LIVE, IntegrationMode.LIVE, "Auth Lab Live", "auth-lab-live");
        seed(TEST, IntegrationMode.TEST, "Auth Lab Test", "auth-lab-test");
    }

    @Test
    void missingBearerIsUnauthorized() throws Exception {
        mockMvc.perform(multipart("/v1/authenticity/check").file(reference()).param("documentId", DOCUMENT))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("unauthorized"));
    }

    @Test
    void templatesListsTheLoadedGabarit() throws Exception {
        mockMvc.perform(get("/v1/authenticity/templates").header("Authorization", "Bearer " + LIVE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.templates", hasSize(4)))
                .andExpect(jsonPath(
                        "$.templates[*].documentId",
                        containsInAnyOrder(
                                "CANADA_PERMANENT_RESIDENT:2015:BACK",
                                "CANADA_PERMANENT_RESIDENT:2015:FRONT",
                                "QUEBEC_DRIVER_LICENSE:2024:BACK",
                                "QUEBEC_DRIVER_LICENSE:2024:FRONT")))
                .andExpect(jsonPath("$.templates[?(@.documentId == '" + DOCUMENT + "')].code", hasItem("QUEBEC_DRIVER_LICENSE")))
                .andExpect(jsonPath("$.templates[?(@.documentId == '" + DOCUMENT + "')].version", hasItem("2024")))
                .andExpect(jsonPath("$.templates[?(@.documentId == '" + DOCUMENT + "')].side", hasItem("FRONT")))
                .andExpect(jsonPath("$.templates[?(@.documentId == '" + DOCUMENT + "')].imageWidth", hasItem(1280)))
                .andExpect(jsonPath("$.templates[?(@.documentId == '" + DOCUMENT + "')].imageHeight", hasItem(813)))
                .andExpect(jsonPath("$.templates[?(@.documentId == '" + DOCUMENT + "')].canvasWidth", hasItem(729)))
                .andExpect(jsonPath("$.templates[?(@.documentId == '" + DOCUMENT + "')].canvasHeight", hasItem(457)))
                .andExpect(jsonPath("$.templates[?(@.documentId == '" + DOCUMENT + "')].fixedCount", hasItem(5)))
                .andExpect(jsonPath("$.templates[?(@.documentId == '" + DOCUMENT + "')].fieldCount", hasItem(16)));
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/v1/authenticity/check']").exists())
                .andExpect(jsonPath("$.paths['/v1/authenticity/templates']").exists())
                .andExpect(jsonPath("$.components.schemas.RectifyBlock").exists())
                .andExpect(jsonPath("$.components.schemas.ProjectedBoxBlock").exists())
                .andExpect(jsonPath("$.components.schemas.FixedBlock").exists());
    }

    @Test
    void referenceRectifiesWithoutCreatingVerification() throws Exception {
        long before = verifications.count();
        MvcResult first = mockMvc.perform(multipart("/v1/authenticity/check")
                        .file(reference())
                        .param("documentId", DOCUMENT)
                        .param("until", "rectify")
                        .header("Authorization", "Bearer " + LIVE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pipeline").value("template-1"))
                .andExpect(jsonPath("$.stoppedAt").value("rectify"))
                .andExpect(jsonPath("$.documentId").value(DOCUMENT))
                .andExpect(jsonPath("$.verdict").value(nullValue()))
                .andExpect(jsonPath("$.codes", hasSize(0)))
                .andExpect(jsonPath("$.rectify.method", anyOf(is("corners"), is("features"))))
                .andExpect(jsonPath("$.rectify.canvas[0]").value(729))
                .andExpect(jsonPath("$.rectify.canvas[1]").value(457))
                .andExpect(jsonPath("$.project").value(nullValue()))
                .andExpect(jsonPath("$.fixed").value(nullValue()))
                .andExpect(jsonPath("$.positions").value(nullValue()))
                .andExpect(content().string(not(containsString("suspect"))))
                .andReturn();
        MvcResult second = mockMvc.perform(multipart("/v1/authenticity/check")
                        .file(reference())
                        .param("documentId", DOCUMENT)
                        .param("until", "rectify")
                        .header("Authorization", "Bearer " + LIVE))
                .andExpect(status().isOk())
                .andReturn();
        assertEquals(first.getResponse().getContentAsString(), second.getResponse().getContentAsString());
        assertEquals(before, verifications.count());
    }

    @Test
    void laterStageStillReturnsTheRectifiedCanvas() throws Exception {
        mockMvc.perform(multipart("/v1/authenticity/check")
                        .file(reference())
                        .param("documentId", DOCUMENT)
                        .header("Authorization", "Bearer " + LIVE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stoppedAt").value("decide"))
                .andExpect(jsonPath("$.verdict").value("conforme"))
                .andExpect(jsonPath("$.codes", hasSize(0)))
                .andExpect(jsonPath("$.rectify.canvas[0]").value(729))
                .andExpect(jsonPath("$.rectify.canvas[1]").value(457))
                .andExpect(jsonPath("$.project", hasSize(21)))
                .andExpect(jsonPath("$.fixed", hasSize(15)))
                .andExpect(jsonPath("$.positions", hasSize(16)));
    }

    @Test
    void projectListsEveryNonDocumentBox() throws Exception {
        mockMvc.perform(multipart("/v1/authenticity/check")
                        .file(reference())
                        .param("documentId", DOCUMENT)
                        .param("until", "project")
                        .header("Authorization", "Bearer " + LIVE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stoppedAt").value("project"))
                .andExpect(jsonPath("$.verdict").value(nullValue()))
                .andExpect(jsonPath("$.codes", hasSize(0)))
                .andExpect(jsonPath("$.rectify.canvas[0]").value(729))
                .andExpect(jsonPath("$.rectify.canvas[1]").value(457))
                .andExpect(jsonPath("$.project", hasSize(21)))
                .andExpect(jsonPath("$.project[0].name").value("logo_quebec_texte"))
                .andExpect(jsonPath("$.project[0].role").value("fixed"))
                .andExpect(jsonPath("$.project[0].rectangle[0]").value(27))
                .andExpect(jsonPath("$.project[0].rectangle[1]").value(24))
                .andExpect(jsonPath("$.project[0].rectangle[2]").value(154))
                .andExpect(jsonPath("$.project[0].rectangle[3]").value(44))
                .andExpect(jsonPath("$.fixed").value(nullValue()))
                .andExpect(jsonPath("$.positions").value(nullValue()));
    }

    @Test
    void referenceFixedElementsMatch() throws Exception {
        MvcResult result = mockMvc.perform(multipart("/v1/authenticity/check")
                        .file(reference())
                        .param("documentId", DOCUMENT)
                        .param("until", "fixed")
                        .header("Authorization", "Bearer " + LIVE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stoppedAt").value("fixed"))
                .andExpect(jsonPath("$.verdict").value(nullValue()))
                .andExpect(jsonPath("$.codes", hasSize(0)))
                .andExpect(jsonPath("$.project", hasSize(21)))
                .andExpect(jsonPath("$.fixed", hasSize(15)))
                .andExpect(jsonPath("$.positions").value(nullValue()))
                .andReturn();
        JsonNode fixed = new ObjectMapper().readTree(result.getResponse().getContentAsString()).get("fixed");
        for (JsonNode item : fixed) {
            assertTrue(item.get("score").asDouble() >= 0.75, item.toString());
            assertEquals("conforme", item.get("status").asText(), item.toString());
        }
    }

    @Test
    void maskedMarkIsSuspectWhileTheOthersStayMeasured() throws Exception {
        MvcResult result = mockMvc.perform(multipart("/v1/authenticity/check")
                        .file(maskedLogo())
                        .param("documentId", DOCUMENT)
                        .param("until", "fixed")
                        .header("Authorization", "Bearer " + LIVE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stoppedAt").value("fixed"))
                .andExpect(jsonPath("$.verdict").value(nullValue()))
                .andExpect(jsonPath("$.fixed", hasSize(15)))
                .andExpect(jsonPath("$.positions").value(nullValue()))
                .andReturn();
        JsonNode fixed = new ObjectMapper().readTree(result.getResponse().getContentAsString()).get("fixed");
        boolean logo = false;
        boolean other = false;
        for (JsonNode item : fixed) {
            if ("logo_quebec_texte".equals(item.get("name").asText())) {
                logo = true;
                assertTrue(item.get("score").asDouble() < 0.50, item.toString());
                assertEquals("suspect", item.get("status").asText());
            } else if ("conforme".equals(item.get("status").asText())) {
                other = true;
            }
        }
        assertTrue(logo);
        assertTrue(other);
    }

    @Test
    void oneMaskedMarkDoesNotRefuseThePiece() throws Exception {
        MvcResult result = mockMvc.perform(multipart("/v1/authenticity/check")
                        .file(maskedLogo())
                        .param("documentId", DOCUMENT)
                        .param("until", "decide")
                        .header("Authorization", "Bearer " + LIVE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stoppedAt").value("decide"))
                .andExpect(jsonPath("$.verdict").value("conforme"))
                .andExpect(jsonPath("$.codes", hasSize(0)))
                .andExpect(jsonPath("$.fixed", hasSize(15)))
                .andExpect(jsonPath("$.positions", hasSize(16)))
                .andReturn();
        JsonNode fixed = new ObjectMapper().readTree(result.getResponse().getContentAsString()).get("fixed");
        boolean logo = false;
        boolean other = false;
        for (JsonNode item : fixed) {
            if ("logo_quebec_texte".equals(item.get("name").asText())) {
                logo = true;
                assertEquals("suspect", item.get("status").asText());
            } else if ("conforme".equals(item.get("status").asText())) {
                other = true;
            }
        }
        assertTrue(logo);
        assertTrue(other);
    }

    @Test
    void referencePositionsMatch() throws Exception {
        MvcResult result = mockMvc.perform(multipart("/v1/authenticity/check")
                        .file(reference())
                        .param("documentId", DOCUMENT)
                        .param("until", "positions")
                        .header("Authorization", "Bearer " + LIVE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stoppedAt").value("positions"))
                .andExpect(jsonPath("$.verdict").value(nullValue()))
                .andExpect(jsonPath("$.codes", hasSize(0)))
                .andExpect(jsonPath("$.project", hasSize(21)))
                .andExpect(jsonPath("$.fixed", hasSize(15)))
                .andExpect(jsonPath("$.positions", hasSize(16)))
                .andReturn();
        JsonNode positions = new ObjectMapper().readTree(result.getResponse().getContentAsString()).get("positions");
        for (JsonNode item : positions) {
            assertTrue(item.get("iou").asDouble() >= 0.50, item.toString());
            assertTrue(item.get("deltaPx").asInt() < 35, item.toString());
            assertEquals("conforme", item.get("status").asText(), item.toString());
        }
    }

    @Test
    void shiftedNameThatRemainsInItsWindowStaysConforme() throws Exception {
        MvcResult result = mockMvc.perform(multipart("/v1/authenticity/check")
                        .file(shiftedName())
                        .param("documentId", DOCUMENT)
                        .param("until", "positions")
                        .header("Authorization", "Bearer " + LIVE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verdict").value(nullValue()))
                .andExpect(jsonPath("$.positions", hasSize(16)))
                .andReturn();
        JsonNode positions = new ObjectMapper().readTree(result.getResponse().getContentAsString()).get("positions");
        boolean name = false;
        boolean other = false;
        for (JsonNode item : positions) {
            if ("nom".equals(item.get("name").asText())) {
                name = true;
                assertEquals("conforme", item.get("status").asText(), item.toString());
            } else if ("conforme".equals(item.get("status").asText())) {
                other = true;
            }
        }
        assertTrue(name);
        assertTrue(other);
    }

    @Test
    void imageWithoutACardAsksForReview() throws Exception {
        mockMvc.perform(multipart("/v1/authenticity/check")
                        .file(gray())
                        .param("documentId", DOCUMENT)
                        .param("until", "rectify")
                        .header("Authorization", "Bearer " + LIVE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verdict").value("a_verifier"))
                .andExpect(jsonPath("$.codes", hasSize(1)))
                .andExpect(jsonPath("$.codes[0]").value("alignment_failed"))
                .andExpect(jsonPath("$.rectify").value(nullValue()))
                .andExpect(jsonPath("$.project").value(nullValue()))
                .andExpect(jsonPath("$.fixed").value(nullValue()))
                .andExpect(jsonPath("$.positions").value(nullValue()))
                .andExpect(content().string(not(containsString("suspect"))))
                .andExpect(content().string(not(containsString("FAIL"))));
        mockMvc.perform(multipart("/v1/authenticity/check")
                        .file(gray())
                        .param("documentId", DOCUMENT)
                        .param("until", "project")
                        .header("Authorization", "Bearer " + LIVE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verdict").value("a_verifier"))
                .andExpect(jsonPath("$.project").value(nullValue()));
        mockMvc.perform(multipart("/v1/authenticity/check")
                        .file(gray())
                        .param("documentId", DOCUMENT)
                        .param("until", "fixed")
                        .header("Authorization", "Bearer " + LIVE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verdict").value("a_verifier"))
                .andExpect(jsonPath("$.fixed").value(nullValue()));
        mockMvc.perform(multipart("/v1/authenticity/check")
                        .file(gray())
                        .param("documentId", DOCUMENT)
                        .param("until", "positions")
                        .header("Authorization", "Bearer " + LIVE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verdict").value("a_verifier"))
                .andExpect(jsonPath("$.positions").value(nullValue()));
    }

    @Test
    void testKeyCanCallLab() throws Exception {
        mockMvc.perform(multipart("/v1/authenticity/check")
                        .file(reference())
                        .param("documentId", DOCUMENT)
                        .param("until", "rectify")
                        .header("Authorization", "Bearer " + TEST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pipeline").value("template-1"))
                .andExpect(jsonPath("$.stoppedAt").value("rectify"))
                .andExpect(jsonPath("$.rectify.canvas[0]").value(729));
    }

    @Test
    void undecodableImageIsRejected() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "doc.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[] {1, 2, 3});
        mockMvc.perform(multipart("/v1/authenticity/check")
                        .file(file)
                        .param("documentId", DOCUMENT)
                        .header("Authorization", "Bearer " + LIVE))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("image_rejected"));
    }

    @Test
    void unknownTemplateIsRejected() throws Exception {
        mockMvc.perform(multipart("/v1/authenticity/check")
                        .file(gray())
                        .param("documentId", "UNKNOWN:2024:FRONT")
                        .header("Authorization", "Bearer " + LIVE))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("template_not_found"));
    }

    @Test
    void blankDocumentIdIsRejected() throws Exception {
        mockMvc.perform(multipart("/v1/authenticity/check")
                        .file(new MockMultipartFile("file", "doc.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[] {1, 2, 3}))
                        .param("documentId", "  ")
                        .header("Authorization", "Bearer " + LIVE))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("template_not_found"));
    }

    @Test
    void unknownUntilIsRejected() throws Exception {
        mockMvc.perform(multipart("/v1/authenticity/check")
                        .file(new MockMultipartFile("file", "doc.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[] {1, 2, 3}))
                        .param("documentId", DOCUMENT)
                        .param("until", "content")
                        .header("Authorization", "Bearer " + LIVE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("validation_error"))
                .andExpect(jsonPath("$.error.details[0].field").value("until"));
    }

    private void seed(String rawKey, IntegrationMode mode, String orgName, String slug) {
        Instant now = Instant.parse("2026-11-15T12:00:00Z");
        UUID organizationId = UUID.randomUUID();
        UUID integrationId = UUID.randomUUID();
        organizations.save(new Organization(organizationId, orgName, slug, now));
        integrations.save(new Integration(
                integrationId, organizationId, ProductCode.IDENTITY, mode, mode.name(), now));
        apiKeys.save(new ApiKey(
                UUID.randomUUID(),
                organizationId,
                integrationId,
                rawKey.substring(0, ApiKeyAuthenticator.PREFIX_LENGTH),
                passwordEncoder.encode(rawKey),
                now));
    }

    private static MockMultipartFile reference() throws IOException {
        return new MockMultipartFile(
                "file",
                "reference.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                SpecimenCache.shipped("QUEBEC_DRIVER_LICENSE_2024_FRONT"));
    }

    private static MockMultipartFile maskedLogo() throws IOException {
        BufferedImage image;
        image = ImageIO.read(new ByteArrayInputStream(SpecimenCache.shipped("QUEBEC_DRIVER_LICENSE_2024_FRONT")));
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(new Color(180, 180, 180));
        graphics.fillRect(314, 209, 154, 44);
        graphics.dispose();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", bytes);
        return new MockMultipartFile("file", "masked.jpg", MediaType.IMAGE_JPEG_VALUE, bytes.toByteArray());
    }

    private static MockMultipartFile shiftedName() throws IOException {
        BufferedImage image;
        image = ImageIO.read(new ByteArrayInputStream(SpecimenCache.shipped("QUEBEC_DRIVER_LICENSE_2024_FRONT")));
        int x = 584;
        int y = 302;
        int width = 154;
        int height = 15;
        int shift = 40;
        BufferedImage patch = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int row = 0; row < height; row++) {
            for (int column = 0; column < width; column++) {
                patch.setRGB(column, row, image.getRGB(x + column, y + row));
            }
        }
        int fill = image.getRGB(x + width + shift, y);
        for (int row = 0; row < height; row++) {
            for (int column = 0; column < width; column++) {
                image.setRGB(x + column, y + row, fill);
            }
        }
        for (int row = 0; row < height; row++) {
            for (int column = 0; column < width; column++) {
                image.setRGB(x + shift + column, y + row, patch.getRGB(column, row));
            }
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", bytes);
        return new MockMultipartFile("file", "shifted.jpg", MediaType.IMAGE_JPEG_VALUE, bytes.toByteArray());
    }

    private static MockMultipartFile gray() throws IOException {
        BufferedImage image = new BufferedImage(640, 400, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(new Color(180, 180, 180));
        graphics.fillRect(0, 0, 640, 400);
        graphics.dispose();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", bytes);
        return new MockMultipartFile("file", "flat.jpg", MediaType.IMAGE_JPEG_VALUE, bytes.toByteArray());
    }
}
