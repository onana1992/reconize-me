package com.kyc.authenticity;

import java.lang.reflect.Field;
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
 * {@link Loader#getLoadedLibraries()} renvoie une copie : il faut écrire la table privée.
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
            skipHighgui();
            Loader.load(opencv_core.class);
            Loader.load(opencv_imgproc.class);
            Loader.load(opencv_flann.class);
            Loader.load(opencv_features2d.class);
            Loader.load(opencv_calib3d.class);
            loaded = true;
        }
    }

    @SuppressWarnings("unchecked")
    private static void skipHighgui() {
        try {
            Field field = Loader.class.getDeclaredField("loadedLibraries");
            field.setAccessible(true);
            Map<String, String> libraries = (Map<String, String>) field.get(null);
            // Linux garde le motif @.410. Windows le résout en opencv_highgui4100.
            libraries.put("jniopencv_highgui", "skipped");
            libraries.put("opencv_highgui@.410", "skipped");
            libraries.put("opencv_highgui4100", "skipped");
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("OpenCV highgui cannot be skipped", e);
        }
    }
}
