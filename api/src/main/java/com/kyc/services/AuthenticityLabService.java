package com.kyc.services;

import com.kyc.authenticity.AuthenticityTemplate;
import com.kyc.authenticity.AuthenticityVerdict;
import com.kyc.authenticity.BoxProjector;
import com.kyc.authenticity.CardRectifier;
import com.kyc.authenticity.FixedMatcher;
import com.kyc.authenticity.PhotoImage;
import com.kyc.authenticity.PositionMeasurer;
import com.kyc.authenticity.Rectified;
import com.kyc.authenticity.TemplateRegistry;
import com.kyc.config.KycProperties;
import com.kyc.dto.authenticity.AuthenticityCheckResponse;
import com.kyc.dto.authenticity.AuthenticityTemplatesResponse;
import com.kyc.dto.authenticity.FixedBlock;
import com.kyc.dto.authenticity.PositionBlock;
import com.kyc.dto.authenticity.ProjectedBoxBlock;
import com.kyc.dto.authenticity.RectifyBlock;
import com.kyc.web.ApiException;
import com.kyc.web.ErrorDetail;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AuthenticityLabService {

    static final List<String> STAGES = List.of("rectify", "project", "fixed", "positions", "decide");

    private static final Logger log = LoggerFactory.getLogger(AuthenticityLabService.class);

    private final KycProperties properties;
    private final TemplateRegistry registry;
    private final CardRectifier rectifier;
    private final BoxProjector projector;
    private final FixedMatcher fixedMatcher;
    private final PositionMeasurer positionMeasurer;

    public AuthenticityLabService(
            KycProperties properties,
            TemplateRegistry registry,
            CardRectifier rectifier,
            BoxProjector projector,
            FixedMatcher fixedMatcher,
            PositionMeasurer positionMeasurer) {
        this.properties = properties;
        this.registry = registry;
        this.rectifier = rectifier;
        this.projector = projector;
        this.fixedMatcher = fixedMatcher;
        this.positionMeasurer = positionMeasurer;
    }

    public AuthenticityTemplatesResponse templates() {
        requireEnabled();
        List<AuthenticityTemplatesResponse.Template> listed = registry.templates().stream()
                .map(template -> new AuthenticityTemplatesResponse.Template(
                        template.documentId(),
                        template.code(),
                        template.version(),
                        template.side(),
                        template.imageWidth(),
                        template.imageHeight(),
                        template.canvasWidth(),
                        template.canvasHeight(),
                        template.fixedCount(),
                        template.fieldCount()))
                .toList();
        return new AuthenticityTemplatesResponse(listed);
    }

    public AuthenticityCheckResponse check(String documentId, String until, MultipartFile file) {
        requireEnabled();
        String stoppedAt = parseUntil(until);
        if (documentId == null || documentId.isBlank()) {
            throw ApiException.unprocessable("template_not_found", "documentId is required");
        }
        String id = documentId.trim();
        BufferedImage image = requireImage(file);
        AuthenticityTemplate template = registry
                .find(id)
                .orElseThrow(() -> ApiException.unprocessable("template_not_found", "Template not found"));
        long started = System.nanoTime();
        Optional<Rectified> aligned = rectifier.rectify(template, image);
        if (aligned.isEmpty()) {
            AuthenticityVerdict.Decision decision = AuthenticityVerdict.alignmentFailed();
            log.info(
                    "authenticity documentId={} until={} method={} detector={} verdict={} codes={} boxes={} fixed={} positions={} durationMs={}",
                    id,
                    stoppedAt,
                    "",
                    "",
                    decision.verdict(),
                    decision.codes(),
                    "",
                    "",
                    "",
                    elapsed(started));
            return AuthenticityCheckResponse.of(
                    id, stoppedAt, decision.verdict(), decision.codes(), null, null, null, null);
        }
        Rectified rectified = aligned.get();
        List<ProjectedBoxBlock> project = includes(stoppedAt, "project") ? project(template) : null;
        List<FixedBlock> fixed = includes(stoppedAt, "fixed") ? fixed(template, rectified.image()) : null;
        List<PositionBlock> positions = includes(stoppedAt, "positions") ? positions(template, rectified.image()) : null;
        AuthenticityVerdict.Decision decision = includes(stoppedAt, "decide")
                ? AuthenticityVerdict.decide(readings(fixed, positions))
                : new AuthenticityVerdict.Decision(null, List.of());
        log.info(
                "authenticity documentId={} until={} method={} detector={} verdict={} codes={} boxes={} fixed={} positions={} durationMs={}",
                id,
                stoppedAt,
                rectified.method(),
                rectified.detector(),
                decision.verdict(),
                decision.codes(),
                project == null ? "" : project.size(),
                scores(fixed),
                places(positions),
                elapsed(started));
        return AuthenticityCheckResponse.of(
                id,
                stoppedAt,
                decision.verdict(),
                decision.codes(),
                new RectifyBlock(rectified.method(), new int[] {template.canvasWidth(), template.canvasHeight()}),
                project,
                fixed,
                positions);
    }

    private static List<AuthenticityVerdict.Reading> readings(List<FixedBlock> fixed, List<PositionBlock> positions) {
        List<AuthenticityVerdict.Reading> readings = new ArrayList<>();
        if (fixed != null) {
            for (FixedBlock item : fixed) {
                readings.add(new AuthenticityVerdict.Reading(item.status(), item.code()));
            }
        }
        if (positions != null) {
            for (PositionBlock item : positions) {
                readings.add(new AuthenticityVerdict.Reading(item.status(), item.code()));
            }
        }
        return readings;
    }

    private static long elapsed(long started) {
        return (System.nanoTime() - started) / 1_000_000L;
    }

    private List<PositionBlock> positions(AuthenticityTemplate template, BufferedImage aligned) {
        return positionMeasurer.measure(template, aligned).stream()
                .map(score -> new PositionBlock(
                        score.name(), score.role(), score.iou(), score.deltaPx(), score.status(), score.code()))
                .toList();
    }

    private static String places(List<PositionBlock> positions) {
        if (positions == null) {
            return "";
        }
        return positions.stream()
                .map(item -> item.name() + ":" + item.iou() + ":" + item.status())
                .collect(Collectors.joining(","));
    }

    private List<FixedBlock> fixed(AuthenticityTemplate template, BufferedImage aligned) {
        return fixedMatcher.match(template, aligned).stream()
                .map(score -> new FixedBlock(score.name(), score.role(), score.score(), score.shiftPx(), score.status(), null))
                .toList();
    }

    private static String scores(List<FixedBlock> fixed) {
        if (fixed == null) {
            return "";
        }
        return fixed.stream()
                .map(item -> item.name() + ":" + item.score() + ":" + item.status())
                .collect(Collectors.joining(","));
    }

    private List<ProjectedBoxBlock> project(AuthenticityTemplate template) {
        return projector.project(template).stream()
                .map(box -> new ProjectedBoxBlock(
                        box.name(), box.role(), new int[] {box.x(), box.y(), box.width(), box.height()}))
                .toList();
    }

    private static boolean includes(String stoppedAt, String stage) {
        return STAGES.indexOf(stoppedAt) >= STAGES.indexOf(stage);
    }

    private void requireEnabled() {
        if (!properties.authenticity().labEnabled()) {
            throw ApiException.notFound("Not found");
        }
    }

    private static String parseUntil(String until) {
        if (until == null || until.isBlank()) {
            return "decide";
        }
        String stage = until.trim().toLowerCase(Locale.ROOT);
        if (!STAGES.contains(stage)) {
            throw ApiException.validation("Invalid until", List.of(new ErrorDetail("until", "invalid")));
        }
        return stage;
    }

    private static BufferedImage requireImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.unprocessable("image_rejected", "Image is missing or empty");
        }
        String base = mediaType(file.getContentType());
        boolean jpeg = "image/jpeg".equals(base);
        boolean png = "image/png".equals(base);
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw ApiException.unprocessable("image_rejected", "Image is unreadable");
        }
        if ((!jpeg && !png) || (jpeg && !PhotoImage.isJpeg(bytes)) || (png && !PhotoImage.isPng(bytes))) {
            throw ApiException.unprocessable("image_rejected", "Image type is not JPEG or PNG");
        }
        BufferedImage image = PhotoImage.decode(bytes);
        if (image == null) {
            throw ApiException.unprocessable("image_rejected", "Image is unreadable");
        }
        return image;
    }

    private static String mediaType(String contentType) {
        if (contentType == null) {
            return "";
        }
        String base = contentType.toLowerCase(Locale.ROOT).trim();
        int separator = base.indexOf(';');
        return separator < 0 ? base : base.substring(0, separator).trim();
    }
}
