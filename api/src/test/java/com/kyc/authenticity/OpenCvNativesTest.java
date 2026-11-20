package com.kyc.authenticity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Map;
import org.bytedeco.javacpp.Loader;
import org.bytedeco.opencv.global.opencv_features2d;
import org.bytedeco.opencv.opencv_features2d.ORB;
import org.junit.jupiter.api.Test;

class OpenCvNativesTest {

    @Test
    void highguiStaysSkippedAndOrbStillLoads() {
        OpenCvNatives.load();

        Map<String, String> libraries = Loader.getLoadedLibraries();
        assertEquals("skipped", libraries.get("jniopencv_highgui"));
        for (String link : Loader.loadProperties(opencv_features2d.class, Loader.loadProperties(), true)
                .get("platform.link")) {
            if (link.contains("highgui")) {
                assertEquals("skipped", libraries.get(link), link);
            }
        }
        assertNotNull(libraries.get("jniopencv_features2d"));

        ORB orb = ORB.create(200, 1.2f, 8, 31, 0, 2, ORB.HARRIS_SCORE, 31, 20);
        assertNotNull(orb);
        orb.close();
    }
}
