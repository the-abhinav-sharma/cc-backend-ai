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
	    // 1. Read the PNG image
	    BufferedImage image = ImageIO.read(new ByteArrayInputStream(original));
	
	    // 2. Prepare output stream
	    ByteArrayOutputStream baos = new ByteArrayOutputStream();
	
	    // 3. Get a JPEG writer
	    Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
	    if (!writers.hasNext()) {
	        throw new IllegalStateException("No writers found for JPEG");
	    }
	    ImageWriter writer = writers.next();
	
	    try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
	        writer.setOutput(ios);
	
	        // 4. Set compression quality (0.0 to 1.0)
	        // 0.85 (85%) is usually the sweet spot for a massive drop in size with zero noticeable quality loss
	        ImageWriteParam param = writer.getDefaultWriteParam();
	        if (param.canWriteCompressed()) {
	            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
	            param.setCompressionQuality(quality);
	        }
	
	        // 5. Write image
	        writer.write(null, new IIOImage(image, null, null), param);
	    } finally {
	        writer.dispose();
	    }
	
	    return baos.toByteArray();
	}
}