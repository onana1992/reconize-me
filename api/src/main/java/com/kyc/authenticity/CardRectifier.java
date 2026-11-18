package com.kyc.authenticity;

import java.awt.image.BufferedImage;
import java.util.Optional;
import org.bytedeco.javacpp.indexer.DoubleIndexer;
import org.springframework.stereotype.Component;
import org.bytedeco.javacpp.indexer.FloatIndexer;
import org.bytedeco.javacpp.indexer.IntIndexer;
import org.bytedeco.opencv.global.opencv_calib3d;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.global.opencv_features2d;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.opencv_core.DMatchVector;
import org.bytedeco.opencv.opencv_core.DMatchVectorVector;
import org.bytedeco.opencv.opencv_core.KeyPoint;
import org.bytedeco.opencv.opencv_core.KeyPointVector;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.MatVector;
import org.bytedeco.opencv.opencv_core.Scalar;
import org.bytedeco.opencv.opencv_core.Size;
import org.bytedeco.opencv.opencv_core.TermCriteria;
import org.bytedeco.opencv.opencv_features2d.BFMatcher;
import org.bytedeco.opencv.opencv_features2d.Feature2D;
import org.bytedeco.opencv.opencv_features2d.ORB;
import org.bytedeco.opencv.opencv_features2d.SIFT;

/**
 * Ramène une photo au canevas du gabarit. La voie des coins passe en premier. Si le quadrilatère laisse un
 * élément fixe sous {@link TemplateEngine#SIMILARITY_PASS}, ORB puis SIFT sont essayés. Ce quadrilatère n'est
 * gardé que lorsque les deux voies par points échouent.
 */
@Component
public class CardRectifier {

    private static final int FEATURES = 2000;
    private final Object monitor = new Object();
    private final FixedMatcher marks = new FixedMatcher(new BoxProjector());

    public Optional<Rectified> rectify(AuthenticityTemplate template, BufferedImage upright) {
        synchronized (monitor) {
            opencv_core.setRNGSeed(TemplateEngine.RNG_SEED);
            Mat color = bgr(upright);
            try {
                BufferedImage byCorners = null;
                double[][] corners = findCorners(color, template.ratio());
                if (corners != null) {
                    byCorners = warp(color, corners, template.canvasWidth(), template.canvasHeight());
                    if (byCorners != null && marksHold(template, byCorners)) {
                        return Optional.of(new Rectified("corners", "", byCorners));
                    }
                }
                Optional<Rectified> byFeatures = alignByFeatures(color, template);
                if (byFeatures.isPresent()) {
                    return byFeatures;
                }
                if (byCorners != null) {
                    return Optional.of(new Rectified("corners", "", byCorners));
                }
                return Optional.empty();
            } finally {
                color.close();
            }
        }
    }

    private Optional<Rectified> alignByFeatures(Mat color, AuthenticityTemplate template) {
        double[][] byOrb = findByFeatures(color, template, true);
        if (byOrb != null) {
            BufferedImage warped = warp(color, byOrb, template.canvasWidth(), template.canvasHeight());
            if (warped != null) {
                return Optional.of(new Rectified("features", "orb", warped));
            }
        }
        double[][] bySift = findByFeatures(color, template, false);
        if (bySift == null) {
            return Optional.empty();
        }
        BufferedImage warped = warp(color, bySift, template.canvasWidth(), template.canvasHeight());
        if (warped == null) {
            return Optional.empty();
        }
        return Optional.of(new Rectified("features", "sift", warped));
    }

    /** Un gabarit sans marque fixe n'a rien à opposer au quadrilatère. */
    private boolean marksHold(AuthenticityTemplate template, BufferedImage warped) {
        if (template.fixedTargets().isEmpty()) {
            return true;
        }
        for (FixedScore score : marks.match(template, warped)) {
            if (score.score() < TemplateEngine.SIMILARITY_PASS) {
                return false;
            }
        }
        return true;
    }

    private static double[][] findCorners(Mat color, double expectedRatio) {
        Mat gray = new Mat();
        Mat edges = new Mat();
        Mat hierarchy = new Mat();
        MatVector contours = new MatVector();
        try {
            opencv_imgproc.cvtColor(color, gray, opencv_imgproc.COLOR_BGR2GRAY);
            int detectWidth = scaled(color.cols(), color.rows(), true);
            int detectHeight = scaled(color.cols(), color.rows(), false);
            Mat detect = gray;
            Mat resized = null;
            if (detectWidth != color.cols() || detectHeight != color.rows()) {
                resized = new Mat();
                opencv_imgproc.resize(
                        gray, resized, new Size(detectWidth, detectHeight), 0, 0, opencv_imgproc.INTER_AREA);
                detect = resized;
            }
            opencv_imgproc.Canny(detect, edges, 40, 120);
            Mat kernel = opencv_imgproc.getStructuringElement(opencv_imgproc.MORPH_RECT, new Size(3, 3));
            opencv_imgproc.morphologyEx(edges, edges, opencv_imgproc.MORPH_CLOSE, kernel);
            kernel.close();
            opencv_imgproc.findContours(
                    edges, contours, hierarchy, opencv_imgproc.RETR_EXTERNAL, opencv_imgproc.CHAIN_APPROX_SIMPLE);
            double imageArea = (double) detect.cols() * detect.rows();
            double bestArea = 0;
            double[][] best = null;
            for (long i = 0; i < contours.size(); i++) {
                Mat contour = contours.get(i);
                Mat approx = new Mat();
                try {
                    double perimeter = opencv_imgproc.arcLength(contour, true);
                    opencv_imgproc.approxPolyDP(contour, approx, 0.02 * perimeter, true);
                    if (approx.rows() != 4 || !opencv_imgproc.isContourConvex(approx)) {
                        continue;
                    }
                    double[] xs = new double[4];
                    double[] ys = new double[4];
                    try (IntIndexer indexer = approx.createIndexer()) {
                        for (int p = 0; p < 4; p++) {
                            xs[p] = indexer.get(p, 0, 0);
                            ys[p] = indexer.get(p, 0, 1);
                        }
                    }
                    double[][] ordered = order(xs, ys);
                    if (ordered == null || !matchesRatio(ordered, expectedRatio)) {
                        continue;
                    }
                    double area = Math.abs(shoelace(ordered));
                    if (area < TemplateEngine.MIN_CARD_AREA_RATIO * imageArea || area <= bestArea) {
                        continue;
                    }
                    if (stddev(detect, ordered) < 2.0) {
                        continue;
                    }
                    ordered = refine(detect, ordered);
                    if (ordered == null || !matchesRatio(ordered, expectedRatio)) {
                        continue;
                    }
                    bestArea = area;
                    best = scaleUp(ordered, detect.cols(), detect.rows(), color.cols(), color.rows());
                } finally {
                    approx.close();
                }
            }
            if (resized != null) {
                resized.close();
            }
            return best;
        } finally {
            gray.close();
            edges.close();
            hierarchy.close();
            contours.close();
        }
    }

    /**
     * Quatre coins du rectangle document, dans la photo, dans l'ordre haut-gauche, haut-droite, bas-droite,
     * bas-gauche du gabarit.
     */
    private static double[][] findByFeatures(Mat queryColor, AuthenticityTemplate template, boolean orb) {
        Mat queryGray = new Mat();
        Mat referenceColor = bgr(template.reference());
        Mat referenceGray = new Mat();
        try {
            opencv_imgproc.cvtColor(queryColor, queryGray, opencv_imgproc.COLOR_BGR2GRAY);
            opencv_imgproc.cvtColor(referenceColor, referenceGray, opencv_imgproc.COLOR_BGR2GRAY);
            Mat queryDetect = scaledGray(queryGray);
            Mat referenceDetect = scaledGray(referenceGray);
            try {
                return match(queryDetect, referenceDetect, queryColor.cols(), queryColor.rows(), template, orb);
            } finally {
                if (queryDetect != queryGray) {
                    queryDetect.close();
                }
                if (referenceDetect != referenceGray) {
                    referenceDetect.close();
                }
            }
        } finally {
            queryGray.close();
            referenceColor.close();
            referenceGray.close();
        }
    }

    private static double[][] match(
            Mat queryDetect,
            Mat referenceDetect,
            int queryWidth,
            int queryHeight,
            AuthenticityTemplate template,
            boolean orb) {
        Feature2D detector = orb
                ? ORB.create(FEATURES, 1.2f, 8, 31, 0, 2, ORB.HARRIS_SCORE, 31, 20)
                : SIFT.create();
        KeyPointVector queryKeys = new KeyPointVector();
        KeyPointVector referenceKeys = new KeyPointVector();
        Mat queryDesc = new Mat();
        Mat referenceDesc = new Mat();
        Mat queryMask = new Mat();
        Mat referenceMask = new Mat();
        BFMatcher matcher = BFMatcher.create(
                orb ? opencv_core.NORM_HAMMING : opencv_core.NORM_L2, false);
        DMatchVectorVector matches = new DMatchVectorVector();
        try {
            detector.detectAndCompute(queryDetect, queryMask, queryKeys, queryDesc);
            detector.detectAndCompute(referenceDetect, referenceMask, referenceKeys, referenceDesc);
            if (queryDesc.empty() || referenceDesc.empty() || queryKeys.empty() || referenceKeys.empty()) {
                return null;
            }
            matcher.knnMatch(queryDesc, referenceDesc, matches, 2);
            int kept = 0;
            for (long i = 0; i < matches.size(); i++) {
                DMatchVector pair = matches.get(i);
                if (pair.size() >= 2 && pair.get(0).distance() < TemplateEngine.LOWE_RATIO * pair.get(1).distance()) {
                    kept++;
                }
            }
            if (kept < TemplateEngine.MIN_INLIERS) {
                return null;
            }
            Mat source = new Mat(kept, 1, opencv_core.CV_32FC2);
            Mat destination = new Mat(kept, 1, opencv_core.CV_32FC2);
            Mat homography = null;
            Mat inverse = null;
            Mat mask = new Mat();
            try (FloatIndexer sourceIndex = source.createIndexer();
                    FloatIndexer destinationIndex = destination.createIndexer()) {
                int row = 0;
                for (long i = 0; i < matches.size(); i++) {
                    DMatchVector pair = matches.get(i);
                    if (pair.size() < 2
                            || pair.get(0).distance() >= TemplateEngine.LOWE_RATIO * pair.get(1).distance()) {
                        continue;
                    }
                    KeyPoint queryPoint = queryKeys.get(pair.get(0).queryIdx());
                    KeyPoint referencePoint = referenceKeys.get(pair.get(0).trainIdx());
                    sourceIndex.put(row, 0, 0, queryPoint.pt().x());
                    sourceIndex.put(row, 0, 1, queryPoint.pt().y());
                    destinationIndex.put(row, 0, 0, referencePoint.pt().x());
                    destinationIndex.put(row, 0, 1, referencePoint.pt().y());
                    row++;
                }
                homography = opencv_calib3d.findHomography(
                        source,
                        destination,
                        opencv_calib3d.RANSAC,
                        TemplateEngine.RANSAC_REPROJ_PX,
                        mask,
                        2000,
                        0.995);
                if (homography == null || homography.empty() || !invertible(homography)) {
                    return null;
                }
                int inliers = opencv_core.countNonZero(mask);
                if (inliers < TemplateEngine.MIN_INLIERS || inliers < TemplateEngine.MIN_INLIER_RATIO * kept) {
                    return null;
                }
                inverse = new Mat();
                if (opencv_core.invert(homography, inverse) == 0) {
                    return null;
                }
                double queryScaleX = queryDetect.cols() / (double) queryWidth;
                double queryScaleY = queryDetect.rows() / (double) queryHeight;
                double referenceScaleX = referenceDetect.cols() / (double) template.imageWidth();
                double referenceScaleY = referenceDetect.rows() / (double) template.imageHeight();
                double[][] document = documentCorners(template, referenceScaleX, referenceScaleY);
                double[][] projected = new double[4][2];
                Mat inverse64 = to64(inverse);
                try (DoubleIndexer map = inverse64.createIndexer()) {
                    for (int i = 0; i < 4; i++) {
                        double x = document[i][0];
                        double y = document[i][1];
                        double w = map.get(2, 0) * x + map.get(2, 1) * y + map.get(2, 2);
                        if (Math.abs(w) < 1e-8) {
                            return null;
                        }
                        projected[i][0] = (map.get(0, 0) * x + map.get(0, 1) * y + map.get(0, 2)) / w / queryScaleX;
                        projected[i][1] = (map.get(1, 0) * x + map.get(1, 1) * y + map.get(1, 2)) / w / queryScaleY;
                    }
                } finally {
                    if (inverse64 != inverse) {
                        inverse64.close();
                    }
                }
                if (!convex(projected) || !inside(projected, queryWidth, queryHeight)) {
                    return null;
                }
                double area = Math.abs(shoelace(projected));
                if (area < TemplateEngine.MIN_CARD_AREA_RATIO * queryWidth * queryHeight) {
                    return null;
                }
                return projected;
            } finally {
                source.close();
                destination.close();
                mask.close();
                if (homography != null) {
                    homography.close();
                }
                if (inverse != null) {
                    inverse.close();
                }
            }
        } finally {
            detector.close();
            queryKeys.close();
            referenceKeys.close();
            queryDesc.close();
            referenceDesc.close();
            queryMask.close();
            referenceMask.close();
            matcher.close();
            matches.close();
        }
    }

    private static Mat scaledGray(Mat gray) {
        int width = scaled(gray.cols(), gray.rows(), true);
        int height = scaled(gray.cols(), gray.rows(), false);
        if (width == gray.cols() && height == gray.rows()) {
            return gray;
        }
        Mat resized = new Mat();
        opencv_imgproc.resize(gray, resized, new Size(width, height), 0, 0, opencv_imgproc.INTER_AREA);
        return resized;
    }

    private static int scaled(int width, int height, boolean horizontal) {
        int longSide = Math.max(width, height);
        if (longSide <= TemplateEngine.DETECT_LONG_SIDE) {
            return horizontal ? width : height;
        }
        double factor = TemplateEngine.DETECT_LONG_SIDE / (double) longSide;
        int value = (int) Math.round((horizontal ? width : height) * factor);
        return Math.max(1, value);
    }

    private static double[][] documentCorners(
            AuthenticityTemplate template, double scaleX, double scaleY) {
        double x = template.documentX() * scaleX;
        double y = template.documentY() * scaleY;
        double right = (template.documentX() + template.documentWidth()) * scaleX;
        double bottom = (template.documentY() + template.documentHeight()) * scaleY;
        return new double[][] {{x, y}, {right, y}, {right, bottom}, {x, bottom}};
    }

    private static boolean invertible(Mat homography) {
        Mat wide = to64(homography);
        try (DoubleIndexer indexer = wide.createIndexer()) {
            double det = indexer.get(0, 0) * indexer.get(1, 1) - indexer.get(0, 1) * indexer.get(1, 0);
            return Math.abs(det) >= 1e-6;
        } finally {
            if (wide != homography) {
                wide.close();
            }
        }
    }

    private static Mat to64(Mat source) {
        if (source.type() == opencv_core.CV_64FC1) {
            return source;
        }
        Mat converted = new Mat();
        source.convertTo(converted, opencv_core.CV_64FC1);
        return converted;
    }

    private static BufferedImage warp(Mat color, double[][] corners, int canvasWidth, int canvasHeight) {
        Mat source = points(corners);
        Mat destination = points(new double[][] {
            {0, 0},
            {canvasWidth - 1, 0},
            {canvasWidth - 1, canvasHeight - 1},
            {0, canvasHeight - 1}
        });
        Mat transform = opencv_imgproc.getPerspectiveTransform(source, destination);
        Mat warped = new Mat();
        try {
            opencv_imgproc.warpPerspective(
                    color,
                    warped,
                    transform,
                    new Size(canvasWidth, canvasHeight),
                    opencv_imgproc.INTER_LINEAR,
                    opencv_core.BORDER_REPLICATE,
                    new Scalar(0, 0, 0, 0));
            if (warped.empty() || warped.cols() != canvasWidth || warped.rows() != canvasHeight) {
                return null;
            }
            return rgb(warped);
        } finally {
            source.close();
            destination.close();
            transform.close();
            warped.close();
        }
    }

    private static double[][] refine(Mat gray, double[][] corners) {
        Mat mat = points(corners);
        try {
            opencv_imgproc.cornerSubPix(
                    gray,
                    mat,
                    new Size(5, 5),
                    new Size(-1, -1),
                    new TermCriteria(TermCriteria.EPS | TermCriteria.COUNT, 40, 0.001));
            try (FloatIndexer indexer = mat.createIndexer()) {
                double[][] refined = new double[4][2];
                for (int i = 0; i < 4; i++) {
                    refined[i][0] = indexer.get(i, 0, 0);
                    refined[i][1] = indexer.get(i, 0, 1);
                }
                return refined;
            }
        } finally {
            mat.close();
        }
    }

    private static double[][] scaleUp(double[][] corners, int detectWidth, int detectHeight, int fullWidth, int fullHeight) {
        double scaleX = detectWidth / (double) fullWidth;
        double scaleY = detectHeight / (double) fullHeight;
        double[][] full = new double[4][2];
        for (int i = 0; i < 4; i++) {
            full[i][0] = corners[i][0] / scaleX;
            full[i][1] = corners[i][1] / scaleY;
        }
        return full;
    }

    private static boolean matchesRatio(double[][] corners, double expected) {
        if (expected <= 0) {
            return false;
        }
        double top = distance(corners[0], corners[1]);
        double bottom = distance(corners[3], corners[2]);
        double left = distance(corners[0], corners[3]);
        double right = distance(corners[1], corners[2]);
        double height = (left + right) / 2.0;
        if (height < 1) {
            return false;
        }
        double measured = ((top + bottom) / 2.0) / height;
        return Math.abs(measured - expected) / expected <= TemplateEngine.ASPECT_TOLERANCE;
    }

    private static double[][] order(double[] xs, double[] ys) {
        int topLeft = 0;
        int bottomRight = 0;
        int topRight = 0;
        int bottomLeft = 0;
        double minSum = Double.POSITIVE_INFINITY;
        double maxSum = Double.NEGATIVE_INFINITY;
        double maxDiff = Double.NEGATIVE_INFINITY;
        double minDiff = Double.POSITIVE_INFINITY;
        for (int i = 0; i < 4; i++) {
            double sum = xs[i] + ys[i];
            double diff = xs[i] - ys[i];
            if (sum < minSum) {
                minSum = sum;
                topLeft = i;
            }
            if (sum > maxSum) {
                maxSum = sum;
                bottomRight = i;
            }
            if (diff > maxDiff) {
                maxDiff = diff;
                topRight = i;
            }
            if (diff < minDiff) {
                minDiff = diff;
                bottomLeft = i;
            }
        }
        if (topLeft == topRight || topLeft == bottomRight || topLeft == bottomLeft || topRight == bottomRight
                || topRight == bottomLeft || bottomRight == bottomLeft) {
            return null;
        }
        return new double[][] {
            {xs[topLeft], ys[topLeft]},
            {xs[topRight], ys[topRight]},
            {xs[bottomRight], ys[bottomRight]},
            {xs[bottomLeft], ys[bottomLeft]}
        };
    }

    private static boolean convex(double[][] corners) {
        double sign = 0;
        for (int i = 0; i < 4; i++) {
            double[] a = corners[i];
            double[] b = corners[(i + 1) % 4];
            double[] c = corners[(i + 2) % 4];
            double cross = (b[0] - a[0]) * (c[1] - b[1]) - (b[1] - a[1]) * (c[0] - b[0]);
            if (Math.abs(cross) < 1e-6) {
                return false;
            }
            if (sign == 0) {
                sign = cross;
            } else if (sign * cross < 0) {
                return false;
            }
        }
        return true;
    }

    private static boolean inside(double[][] corners, int width, int height) {
        double marginX = TemplateEngine.QUAD_OVERFLOW * width;
        double marginY = TemplateEngine.QUAD_OVERFLOW * height;
        for (double[] corner : corners) {
            if (corner[0] < -marginX || corner[0] > width + marginX || corner[1] < -marginY || corner[1] > height + marginY) {
                return false;
            }
        }
        return true;
    }

    private static double shoelace(double[][] corners) {
        double area = 0;
        for (int i = 0; i < 4; i++) {
            double[] current = corners[i];
            double[] next = corners[(i + 1) % 4];
            area += current[0] * next[1] - next[0] * current[1];
        }
        return area / 2.0;
    }

    private static double distance(double[] a, double[] b) {
        double dx = a[0] - b[0];
        double dy = a[1] - b[1];
        return Math.hypot(dx, dy);
    }

    /** Écart-type des niveaux de gris dans le quadrilatère. Un aplat n'est pas une carte. */
    private static double stddev(Mat gray, double[][] corners) {
        double minX = corners[0][0];
        double maxX = corners[0][0];
        double minY = corners[0][1];
        double maxY = corners[0][1];
        for (double[] corner : corners) {
            minX = Math.min(minX, corner[0]);
            maxX = Math.max(maxX, corner[0]);
            minY = Math.min(minY, corner[1]);
            maxY = Math.max(maxY, corner[1]);
        }
        int x0 = Math.max(0, (int) Math.floor(minX));
        int y0 = Math.max(0, (int) Math.floor(minY));
        int x1 = Math.min(gray.cols() - 1, (int) Math.ceil(maxX));
        int y1 = Math.min(gray.rows() - 1, (int) Math.ceil(maxY));
        if (x1 <= x0 || y1 <= y0) {
            return 0;
        }
        Mat patch = gray.apply(new org.bytedeco.opencv.opencv_core.Rect(x0, y0, x1 - x0, y1 - y0));
        Mat mean = new Mat();
        Mat deviation = new Mat();
        try {
            opencv_core.meanStdDev(patch, mean, deviation);
            try (DoubleIndexer indexer = deviation.createIndexer()) {
                return indexer.get(0);
            }
        } finally {
            patch.close();
            mean.close();
            deviation.close();
        }
    }

    private static Mat points(double[][] corners) {
        Mat mat = new Mat(corners.length, 1, opencv_core.CV_32FC2);
        try (FloatIndexer indexer = mat.createIndexer()) {
            for (int i = 0; i < corners.length; i++) {
                indexer.put(i, 0, 0, (float) corners[i][0]);
                indexer.put(i, 0, 1, (float) corners[i][1]);
            }
        }
        return mat;
    }

    static Mat bgr(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);
        byte[] data = new byte[width * height * 3];
        for (int i = 0; i < pixels.length; i++) {
            int pixel = pixels[i];
            data[i * 3] = (byte) pixel;
            data[i * 3 + 1] = (byte) (pixel >> 8);
            data[i * 3 + 2] = (byte) (pixel >> 16);
        }
        Mat mat = new Mat(height, width, opencv_core.CV_8UC3);
        mat.data().put(data);
        return mat;
    }

    private static BufferedImage rgb(Mat bgr) {
        int width = bgr.cols();
        int height = bgr.rows();
        byte[] data = new byte[width * height * 3];
        bgr.data().get(data);
        int[] pixels = new int[width * height];
        for (int i = 0; i < pixels.length; i++) {
            int blue = data[i * 3] & 0xFF;
            int green = data[i * 3 + 1] & 0xFF;
            int red = data[i * 3 + 2] & 0xFF;
            pixels[i] = (red << 16) | (green << 8) | blue;
        }
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, width, height, pixels, 0, width);
        return image;
    }
}
