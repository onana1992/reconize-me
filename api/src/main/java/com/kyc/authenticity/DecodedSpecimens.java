package com.kyc.authenticity;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Au plus huit photos décodées. La plus ancienne est oubliée, le JPEG reste sur le disque. */
final class DecodedSpecimens {

    static final int LIMIT = 8;

    private final Map<String, BufferedImage> images = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, BufferedImage> eldest) {
            return size() > LIMIT;
        }
    };

    synchronized BufferedImage load(String folder, Path file) {
        BufferedImage cached = images.get(folder);
        if (cached != null) {
            return cached;
        }
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(file);
        } catch (IOException e) {
            throw new IllegalStateException("Gabarit " + folder + " : lecture impossible", e);
        }
        if (PhotoImage.orientation(bytes) != 1) {
            throw new IllegalStateException(
                    "Gabarit " + folder + " : l'orientation EXIF du spécimen doit être 1 ou absente");
        }
        BufferedImage image = PhotoImage.decode(bytes);
        if (image == null) {
            throw new IllegalStateException("Gabarit " + folder + " : le spécimen n'est pas un JPEG ou un PNG décodable");
        }
        images.put(folder, image);
        return image;
    }

    synchronized int size() {
        return images.size();
    }
}
