package com.kyc.authenticity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

/** Charge classpath:authenticity/DOSSIER/ au démarrage. Un dossier invalide empêche le démarrage. */
@Component
public class TemplateRegistry {

    private static final Set<String> ROLES = Set.of("document", "fixed", "value", "region");
    private static final ObjectMapper JSON = new ObjectMapper();

    private final Map<String, AuthenticityTemplate> byId;

    public TemplateRegistry() {
        OpenCvNatives.load();
        List<AuthenticityTemplate> loaded = new ArrayList<>();
        try {
            Resource[] files = new PathMatchingResourcePatternResolver()
                    .getResources("classpath*:authenticity/*/annotations.coco.json");
            for (Resource coco : files) {
                loaded.add(readResource(coco));
            }
        } catch (IOException e) {
            throw new IllegalStateException("Lecture des gabarits impossible", e);
        }
        this.byId = index(loaded);
    }

    public List<AuthenticityTemplate> templates() {
        return byId.values().stream()
                .sorted(Comparator.comparing(AuthenticityTemplate::documentId))
                .toList();
    }

    public Optional<AuthenticityTemplate> find(String documentId) {
        return Optional.ofNullable(byId.get(documentId));
    }

    /** Lecture d'un dossier déjà sur disque. Utilisée par les tests du registre. */
    public static AuthenticityTemplate readDirectory(Path directory) {
        String folder = directory.getFileName().toString();
        Path jpg = directory.resolve("reference.jpg");
        Path png = directory.resolve("reference.png");
        boolean hasJpg = Files.isRegularFile(jpg);
        boolean hasPng = Files.isRegularFile(png);
        if (hasJpg == hasPng) {
            throw invalid(folder, "il faut un seul fichier reference.jpg ou reference.png");
        }
        Path coco = directory.resolve("annotations.coco.json");
        if (!Files.isRegularFile(coco)) {
            throw invalid(folder, "annotations.coco.json est absent");
        }
        try {
            return load(folder, Files.readAllBytes(hasJpg ? jpg : png), Files.readAllBytes(coco));
        } catch (IOException e) {
            throw invalid(folder, "lecture impossible");
        }
    }

    public static Map<String, AuthenticityTemplate> index(List<AuthenticityTemplate> loaded) {
        Map<String, AuthenticityTemplate> map = new LinkedHashMap<>();
        for (AuthenticityTemplate template : loaded) {
            if (map.put(template.documentId(), template) != null) {
                throw invalid(template.documentId(), "identifiant en double");
            }
        }
        return Map.copyOf(map);
    }

    public static AuthenticityTemplate load(String folder, byte[] imageBytes, byte[] cocoBytes) {
        if (PhotoImage.orientation(imageBytes) != 1) {
            throw invalid(folder, "l'orientation EXIF du spécimen doit être 1 ou absente");
        }
        BufferedImage image = PhotoImage.decode(imageBytes);
        if (image == null) {
            throw invalid(folder, "le spécimen n'est pas un JPEG ou un PNG décodable");
        }
        JsonNode root;
        try {
            root = JSON.readTree(cocoBytes);
        } catch (IOException e) {
            throw invalid(folder, "le COCO n'est pas un JSON lisible");
        }
        JsonNode images = array(root, "images", folder);
        if (images.size() != 1) {
            throw invalid(folder, "le COCO doit décrire une seule image");
        }
        JsonNode imageNode = images.get(0);
        int width = whole(imageNode, "width", folder);
        int height = whole(imageNode, "height", folder);
        if (width <= 0 || height <= 0) {
            throw invalid(folder, "les dimensions COCO doivent être positives");
        }
        if (image.getWidth() != width || image.getHeight() != height) {
            throw invalid(
                    folder,
                    "la taille du spécimen ("
                            + image.getWidth()
                            + "x"
                            + image.getHeight()
                            + ") diffère du COCO ("
                            + width
                            + "x"
                            + height
                            + ")");
        }
        int imageId = whole(imageNode, "id", folder);

        JsonNode categories = array(root, "categories", folder);
        Map<Integer, Category> byCategory = new LinkedHashMap<>();
        Category documentCategory = null;
        int fixedCount = 0;
        int fieldCount = 0;
        for (JsonNode category : categories) {
            int id = whole(category, "id", folder);
            String name = text(category, "name", folder);
            String role = text(category, "role", folder);
            if (!ROLES.contains(role)) {
                throw invalid(folder, "rôle inconnu : " + role);
            }
            if (name.isBlank() || byCategory.put(id, new Category(id, name, role)) != null) {
                throw invalid(folder, "catégories illisibles ou en double");
            }
            if ("document".equals(role)) {
                if (documentCategory != null) {
                    throw invalid(folder, "il faut une seule catégorie document");
                }
                documentCategory = byCategory.get(id);
            } else if ("fixed".equals(role)) {
                fixedCount++;
            } else {
                fieldCount++;
            }
        }
        if (documentCategory == null) {
            throw invalid(folder, "il faut une seule catégorie document");
        }

        JsonNode annotations = array(root, "annotations", folder);
        if (annotations.size() != byCategory.size()) {
            throw invalid(folder, "chaque catégorie doit avoir une annotation");
        }
        Box document = null;
        List<SourceBox> boxes = new ArrayList<>();
        List<SourceBox> fixedTargets = new ArrayList<>();
        List<SourceBox> positionTargets = new ArrayList<>();
        Set<Integer> seen = new java.util.HashSet<>();
        for (JsonNode annotation : annotations) {
            int categoryId = whole(annotation, "category_id", folder);
            Category category = byCategory.get(categoryId);
            if (category == null || !seen.add(categoryId)) {
                throw invalid(folder, "annotation sans catégorie ou en double");
            }
            if (whole(annotation, "image_id", folder) != imageId) {
                throw invalid(folder, "annotation hors de l'image du gabarit");
            }
            Box box = box(annotation.get("bbox"), width, height, folder, "bbox");
            if ("document".equals(category.role())) {
                document = box;
            }
            JsonNode attributes = annotation.get("attributes");
            if (attributes != null && !attributes.isNull()) {
                if (!"value".equals(category.role())) {
                    throw invalid(folder, "les attributs sont réservés aux catégories value");
                }
                insideParent(attributes.get("bbox_value"), box, folder, "bbox_value");
                insideParent(attributes.get("bbox_label"), box, folder, "bbox_label");
            }
        }
        if (document == null || seen.size() != byCategory.size()) {
            throw invalid(folder, "chaque catégorie doit avoir une annotation");
        }
        for (JsonNode annotation : annotations) {
            Category category = byCategory.get(whole(annotation, "category_id", folder));
            if ("document".equals(category.role())) {
                continue;
            }
            Box box = box(annotation.get("bbox"), width, height, folder, "bbox");
            if (!contains(document, box)) {
                throw invalid(folder, "une boîte sort du rectangle document");
            }
            boxes.add(new SourceBox(category.name(), category.role(), box.x(), box.y(), box.width(), box.height()));
            if ("fixed".equals(category.role())) {
                fixedTargets.add(new SourceBox(category.name(), category.role(), box.x(), box.y(), box.width(), box.height()));
            }
            JsonNode attributes = annotation.get("attributes");
            if (attributes != null && !attributes.isNull()) {
                JsonNode label = attributes.get("bbox_label");
                if (label != null && !label.isNull()) {
                    Box labelBox = box(label, width, height, folder, "bbox_label");
                    fixedTargets.add(new SourceBox(
                            category.name(), category.role(), labelBox.x(), labelBox.y(), labelBox.width(), labelBox.height()));
                }
            }
            if ("value".equals(category.role()) || "region".equals(category.role())) {
                Box expected = box;
                if (attributes != null && !attributes.isNull()) {
                    JsonNode valueBox = attributes.get("bbox_value");
                    if (valueBox != null && !valueBox.isNull()) {
                        expected = box(valueBox, width, height, folder, "bbox_value");
                    }
                }
                positionTargets.add(new SourceBox(
                        category.name(), category.role(), expected.x(), expected.y(), expected.width(), expected.height()));
            }
        }

        String[] parts = folder.split("_");
        if (parts.length < 3) {
            throw invalid(folder, "le nom de dossier ne donne pas code, version et face");
        }
        String side = parts[parts.length - 1];
        String version = parts[parts.length - 2];
        if ((!"FRONT".equals(side) && !"BACK".equals(side)) || version.isBlank()) {
            throw invalid(folder, "la face doit être FRONT ou BACK");
        }
        String code = String.join("_", java.util.Arrays.copyOf(parts, parts.length - 2));
        if (code.isBlank()) {
            throw invalid(folder, "le code du gabarit est vide");
        }
        return new AuthenticityTemplate.Spec()
                .withCode(code)
                .withVersion(version)
                .withSide(side)
                .withImageWidth(width)
                .withImageHeight(height)
                .withDocumentX(document.x())
                .withDocumentY(document.y())
                .withDocumentWidth(document.width())
                .withDocumentHeight(document.height())
                .withFixedCount(fixedCount)
                .withFieldCount(fieldCount)
                .withBoxes(List.copyOf(boxes))
                .withFixedTargets(List.copyOf(fixedTargets))
                .withPositionTargets(List.copyOf(positionTargets))
                .withReference(image)
                .build();
    }

    private static AuthenticityTemplate readResource(Resource coco) throws IOException {
        String folder = folderName(coco);
        // Dans le jar nested de Spring Boot, createRelative().exists() répond vrai
        // pour reference.jpg et reference.png, même si un seul des deux est présent.
        ClassLoader loader = TemplateRegistry.class.getClassLoader();
        if (loader == null) {
            loader = Thread.currentThread().getContextClassLoader();
        }
        String base = "authenticity/" + folder + "/";
        boolean hasJpg = loader.getResource(base + "reference.jpg") != null;
        boolean hasPng = loader.getResource(base + "reference.png") != null;
        if (hasJpg == hasPng) {
            throw invalid(folder, "il faut un seul fichier reference.jpg ou reference.png");
        }
        String imageName = hasJpg ? "reference.jpg" : "reference.png";
        try (InputStream imageStream = loader.getResourceAsStream(base + imageName);
                InputStream cocoStream = loader.getResourceAsStream(base + "annotations.coco.json")) {
            if (imageStream == null || cocoStream == null) {
                throw invalid(folder, "lecture impossible");
            }
            return load(folder, imageStream.readAllBytes(), cocoStream.readAllBytes());
        }
    }

    private static String folderName(Resource coco) throws IOException {
        String uri = URLDecoder.decode(coco.getURI().toString(), StandardCharsets.UTF_8);
        String marker = "authenticity/";
        int start = uri.indexOf(marker);
        if (start < 0) {
            throw new IllegalStateException("Gabarit illisible : chemin hors authenticity/");
        }
        String rest = uri.substring(start + marker.length());
        int slash = rest.indexOf('/');
        if (slash <= 0) {
            throw new IllegalStateException("Gabarit illisible : dossier absent");
        }
        return rest.substring(0, slash);
    }

    private static JsonNode array(JsonNode root, String field, String folder) {
        JsonNode node = root.get(field);
        if (node == null || !node.isArray()) {
            throw invalid(folder, field + " est absent");
        }
        return node;
    }

    private static String text(JsonNode node, String field, String folder) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual()) {
            throw invalid(folder, field + " est absent");
        }
        return value.asText();
    }

    private static int whole(JsonNode node, String field, String folder) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || !value.isNumber()) {
            throw invalid(folder, field + " n'est pas un entier");
        }
        double number = value.asDouble();
        if (Double.isNaN(number) || number != Math.rint(number) || number > Integer.MAX_VALUE || number < Integer.MIN_VALUE) {
            throw invalid(folder, field + " n'est pas un entier");
        }
        return (int) number;
    }

    private static Box box(JsonNode node, int imageWidth, int imageHeight, String folder, String label) {
        if (node == null || !node.isArray() || node.size() != 4) {
            throw invalid(folder, label + " doit contenir quatre nombres");
        }
        int x = wholeNumber(node.get(0), folder, label);
        int y = wholeNumber(node.get(1), folder, label);
        int width = wholeNumber(node.get(2), folder, label);
        int height = wholeNumber(node.get(3), folder, label);
        if (width <= 0 || height <= 0 || x < 0 || y < 0 || x > imageWidth - width || y > imageHeight - height) {
            throw invalid(folder, label + " sort de l'image ou a une taille vide");
        }
        return new Box(x, y, width, height);
    }

    private static void insideParent(JsonNode node, Box parent, String folder, String label) {
        if (node == null || node.isNull()) {
            return;
        }
        Box box = box(node, Integer.MAX_VALUE, Integer.MAX_VALUE, folder, label);
        if (!contains(parent, box)) {
            throw invalid(folder, label + " sort de sa bbox");
        }
    }

    private static int wholeNumber(JsonNode value, String folder, String label) {
        if (value == null || !value.isNumber()) {
            throw invalid(folder, label + " doit contenir quatre nombres");
        }
        double number = value.asDouble();
        if (Double.isNaN(number) || number != Math.rint(number) || number > Integer.MAX_VALUE || number < Integer.MIN_VALUE) {
            throw invalid(folder, label + " doit contenir quatre nombres entiers");
        }
        return (int) number;
    }

    private static boolean contains(Box outer, Box inner) {
        return inner.x() >= outer.x()
                && inner.y() >= outer.y()
                && inner.x() <= outer.x() + outer.width() - inner.width()
                && inner.y() <= outer.y() + outer.height() - inner.height();
    }

    private static IllegalStateException invalid(String folder, String rule) {
        return new IllegalStateException("Gabarit " + folder + " : " + rule);
    }

    private record Category(int id, String name, String role) {}

    private record Box(int x, int y, int width, int height) {}
}
