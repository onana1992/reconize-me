package com.kyc.authenticity;

import org.bytedeco.javacpp.Loader;
import org.bytedeco.opencv.global.opencv_calib3d;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.global.opencv_features2d;
import org.bytedeco.opencv.global.opencv_flann;
import org.bytedeco.opencv.global.opencv_highgui;
import org.bytedeco.opencv.global.opencv_imgcodecs;
import org.bytedeco.opencv.global.opencv_imgproc;

/**
 * Charge les bibliothèques natives dans l'ordre des dépendances.
 * Sans highgui avant features2d, ORB et SIFT échouent au démarrage sur Linux
 * ({@code no jniopencv_highgui in java.library.path}).
 */
public final class OpenCvNatives {

    private static boolean loaded;

    private OpenCvNatives() {}

    public static void load() {
        if (loaded) {
            return;
        }
        synchronized (OpenCvNatives.class) {
            if (loaded) {
                return;
            }
            Loader.load(opencv_core.class);
            Loader.load(opencv_imgproc.class);
            Loader.load(opencv_imgcodecs.class);
            Loader.load(opencv_flann.class);
            Loader.load(opencv_highgui.class);
            Loader.load(opencv_features2d.class);
            Loader.load(opencv_calib3d.class);
            loaded = true;
        }
    }
}
