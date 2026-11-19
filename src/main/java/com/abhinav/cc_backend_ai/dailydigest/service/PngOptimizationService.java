package com.abhinav.cc_backend_ai.dailydigest.service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

import org.springframework.stereotype.Component;

@Component
public class PngOptimizationService {

	public byte[] optimize(byte[] original, float quality) throws IOException {
		BufferedImage image = ImageIO.read(new ByteArrayInputStream(original));
		if (image == null) {
			throw new IllegalArgumentException("Unable to decode image bytes for optimization.");
		}

		ByteArrayOutputStream baos = new ByteArrayOutputStream(512 * 1024);
		Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
		if (!writers.hasNext()) {
			image.flush();
			throw new IllegalStateException("No writers found for JPEG");
		}
		ImageWriter writer = writers.next();

		try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
			writer.setOutput(ios);
			ImageWriteParam param = writer.getDefaultWriteParam();
			if (param.canWriteCompressed()) {
				param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
				param.setCompressionQuality(quality);
			}
			writer.write(null, new IIOImage(image, null, null), param);
		} finally {
			writer.dispose();
			image.flush();
		}

		return baos.toByteArray();
	}
}