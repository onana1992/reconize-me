package com.kyc.authenticity;

import java.util.Map;
import org.bytedeco.javacpp.Loader;
import org.bytedeco.opencv.global.opencv_calib3d;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.global.opencv_features2d;
import org.bytedeco.opencv.global.opencv_flann;
import org.bytedeco.opencv.global.opencv_imgproc;

/**
 * Charge les bibliothèques natives utiles au redressement.
 * Le module features2d hérite de highgui dans JavaCPP, et highgui exige GTK.
 * Sur un serveur ou la CI Linux, GTK est absent : le chargement tombe avec
 * {@code no jniopencv_highgui in java.library.path}. ORB et SIFT n'utilisent pas
 * highgui, donc ce module est marqué comme déjà chargé et n'est pas ouvert.
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
            Map<String, String> libraries = Loader.getLoadedLibraries();
            libraries.put("jniopencv_highgui", "skipped");
            libraries.put("opencv_highgui4100", "skipped");
            Loader.load(opencv_core.class);
            Loader.load(opencv_imgproc.class);
            Loader.load(opencv_flann.class);
            Loader.load(opencv_features2d.class);
            Loader.load(opencv_calib3d.class);
            loaded = true;
        }
    }
}
