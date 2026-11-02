package dev.skinstudio.service;

import dev.skinstudio.model.SkinModel;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;

public final class SkinValidator {
    public static final int MAX_BYTES = 2 * 1024 * 1024;
    public record Validated(int width, int height, SkinModel model) {}

    public Validated validate(byte[] bytes) throws IOException {
        if (bytes.length == 0 || bytes.length > MAX_BYTES) throw new IOException("PNG must be between 1 byte and 2 MB.");
        byte[] pngSignature = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10};
        if (bytes.length < pngSignature.length) throw new IOException("The file is not a PNG image.");
        for (int i = 0; i < pngSignature.length; i++) if (bytes[i] != pngSignature[i]) throw new IOException("The file is not a PNG image.");
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IOException("The file is not a readable PNG image.");
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (!(width == 64 && (height == 64 || height == 32))) {
                    throw new IOException("Unsupported skin dimensions. Use a standard 64×64 or legacy 64×32 Minecraft skin.");
                }
                BufferedImage image = reader.read(0);
                if (image == null) throw new IOException("The file is not a readable PNG image.");
                SkinModel model = height == 32 ? SkinModel.CLASSIC : SkinModel.UNKNOWN;
                return new Validated(width, height, model);
            } finally { reader.dispose(); }
        }
    }

}
