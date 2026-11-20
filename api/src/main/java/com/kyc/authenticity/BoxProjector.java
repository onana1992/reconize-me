package com.kyc.authenticity;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Ramène chaque boîte non {@code document} au rectangle de la carte, puis à l'échelle du canevas.
 * Le document COCO est un rectangle droit : la projection est une translation suivie de cette échelle.
 */
@Component
public class BoxProjector {

    public List<ProjectedBox> project(AuthenticityTemplate template) {
        List<ProjectedBox> projected = new ArrayList<>();
        for (SourceBox box : template.boxes()) {
            projected.add(projectOne(template, box));
        }
        return List.copyOf(projected);
    }

    /** Une boîte de la référence, dans le même repère que {@link #project}. */
    public ProjectedBox projectOne(AuthenticityTemplate template, SourceBox box) {
        int documentWidth = template.documentWidth();
        int documentHeight = template.documentHeight();
        int canvasWidth = template.canvasWidth();
        int canvasHeight = template.canvasHeight();
        int x = map(box.x() - template.documentX(), documentWidth, canvasWidth);
        int y = map(box.y() - template.documentY(), documentHeight, canvasHeight);
        int right = map(box.x() - template.documentX() + box.width(), documentWidth, canvasWidth);
        int bottom = map(box.y() - template.documentY() + box.height(), documentHeight, canvasHeight);
        int width = right - x;
        int height = bottom - y;
        if (x < 0 || y < 0 || width <= 0 || height <= 0 || right > canvasWidth || bottom > canvasHeight) {
            throw new IllegalStateException("Boîte hors canevas : " + box.name());
        }
        return new ProjectedBox(box.name(), box.role(), x, y, width, height);
    }

    /** Bord entier sur le canevas. Le bord source {@code from} tombe sur {@code to}. */
    private static int map(int delta, int from, int to) {
        if (from == to) {
            return delta;
        }
        return (int) ((long) delta * to / from);
    }
}
