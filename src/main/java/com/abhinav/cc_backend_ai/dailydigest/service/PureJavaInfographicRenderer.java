package com.abhinav.cc_backend_ai.dailydigest.service;

import com.abhinav.cc_backend_ai.dailydigest.model.NewsDigest;
import com.abhinav.cc_backend_ai.dailydigest.model.NewsStory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

@Service
@Slf4j
public class PureJavaInfographicRenderer {

	private static final List<String> ORDER = List.of("World", "India", "Delhi NCR", "Chandigarh");
	private final ReentrantLock renderLock = new ReentrantLock();

	public byte[] render(NewsDigest digest, String digestDate) throws Exception {
		log.info("[InfographicRenderer] Render execution requested for date: {}", digestDate);
		if (!renderLock.tryLock()) {
			log.warn("[InfographicRenderer] Render conflict: Another infographic thread is already active.");
			throw new IllegalStateException("Another infographic render is already running");
		}
		long startTime = System.currentTimeMillis();
		try {
			byte[] result = generateInfographicImage(digest, digestDate);
			log.info("[InfographicRenderer] Render completed successfully in {} ms (Size: {} bytes)", 
					(System.currentTimeMillis() - startTime), result.length);
			return result;
		} catch (Exception e) {
			log.error("[InfographicRenderer] Render failed after {} ms: {}", 
					(System.currentTimeMillis() - startTime), e.getMessage(), e);
			throw e;
		} finally {
			renderLock.unlock();
		}
	}

	private byte[] generateInfographicImage(NewsDigest digest, String digestDate) throws IOException {
		int width = 1080; // 540px crisp mobile base width scaled 2x
		int padding = 32;
		int contentWidth = width - (padding * 2);

		log.info("[InfographicRenderer] Grouping stories by priority categories...");
		Map<String, List<NewsStory>> grouped = new LinkedHashMap<>();
		int totalStoriesCount = 0;
		for (String cat : ORDER) {
			List<NewsStory> stories = digest.stories().stream()
					.filter(s -> cat.equalsIgnoreCase(s.category()))
					.toList();
			if (!stories.isEmpty()) {
				grouped.put(cat, stories);
				totalStoriesCount += stories.size();
				log.info("   -> Category '{}': {} stories found", cat, stories.size());
			}
		}
		log.info("[InfographicRenderer] Total stories grouped across categories: {}", totalStoriesCount);

		// Dummy Graphics2D context for accurate font metrics measurement during height calculation
		BufferedImage dummyCanvas = new BufferedImage(width, 100, BufferedImage.TYPE_INT_RGB);
		Graphics2D dummyG2d = dummyCanvas.createGraphics();
		dummyG2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

		Font headlineFont = new Font("Arial", Font.BOLD, 36);
		Font summaryFont = new Font("Arial", Font.PLAIN, 32);

		int cardInnerMargin = 28;
		int textWidth = contentWidth - (cardInnerMargin * 2);
		int imgW = contentWidth - (cardInnerMargin * 2);
		int imgH = 460; // Standard layout photo container height
		int imageToHeadlineGap = 45; // Generous space between image and headline title

		log.info("[InfographicRenderer] Calculating dynamic canvas height...");
		int estimatedHeight = 320; // Header banner
		for (Map.Entry<String, List<NewsStory>> entry : grouped.entrySet()) {
			estimatedHeight += 90; // Section title & padding
			for (NewsStory story : entry.getValue()) {
				int headlineHeight = measureWrappedTextHeight(dummyG2d, story.headline(), headlineFont, textWidth, 46, 3);
				int summaryHeight = measureWrappedTextHeight(dummyG2d, story.summary(), summaryFont, textWidth, 42, 4);
				int cardContentHeight = (cardInnerMargin * 2) + imgH + imageToHeadlineGap + headlineHeight + 16 + summaryHeight + 24 + 30;
				estimatedHeight += cardContentHeight + 28; // Card + gap
			}
		}
		estimatedHeight += 200; // Footer
		dummyG2d.dispose();
		dummyCanvas.flush();
		log.info("[InfographicRenderer] Canvas dimensions calculated: Width = {}, Height = {}px", width, estimatedHeight);

		BufferedImage canvas = new BufferedImage(width, estimatedHeight, BufferedImage.TYPE_INT_RGB);
		Graphics2D g2d = canvas.createGraphics();

		try {
			g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
			g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

			// Background fill (#edf2f8)
			g2d.setColor(new Color(237, 242, 248));
			g2d.fillRect(0, 0, width, estimatedHeight);

			int y = padding;

			// 1. HEADER BANNER
			log.info("[InfographicRenderer] Drawing header banner...");
			GradientPaint headerGradient = new GradientPaint(
					padding, y, new Color(7, 29, 67),
					padding + contentWidth, y + 260, new Color(6, 68, 123)
			);
			g2d.setPaint(headerGradient);
			g2d.fillRoundRect(padding, y, contentWidth, 260, 32, 32);

			g2d.setColor(new Color(124, 224, 255));
			g2d.setFont(new Font("Arial", Font.BOLD, 20));
			g2d.drawString("YOUR DAILY NEWS BRIEFING @ 8.2.8", padding + 40, y + 48);

			g2d.setColor(Color.WHITE);
			g2d.setFont(new Font("Arial", Font.BOLD, 60));
			g2d.drawString("THE DAILY ", padding + 40, y + 122);
			g2d.setColor(new Color(42, 196, 245));
			g2d.drawString("20", padding + 370, y + 122);

			g2d.setColor(new Color(211, 231, 255));
			g2d.setFont(new Font("Arial", Font.PLAIN, 24));
			g2d.drawString(digestDate != null ? digestDate : LocalDate.now().format(DateTimeFormatter.ofPattern("dd-MMM-yyyy")), padding + 40, y + 172);

			g2d.setColor(new Color(54, 113, 167));
			g2d.setStroke(new BasicStroke(2.5f));
			g2d.drawLine(padding + 40, y + 200, padding + contentWidth - 40, y + 200);

			g2d.setColor(new Color(211, 231, 255));
			g2d.setFont(new Font("Arial", Font.BOLD, 18));
			g2d.drawString("WORLD   •   INDIA   •   DELHI NCR   •   CHANDIGARH", padding + 40, y + 236);

			y += 300;

			int storyNumber = 0;

			// 2. SECTIONS & VERTICAL CARDS
			for (Map.Entry<String, List<NewsStory>> entry : grouped.entrySet()) {
				String category = entry.getKey();
				List<NewsStory> stories = entry.getValue();

				Color catColor = getCategoryColor(category);
				log.info("[InfographicRenderer] Rendering category section: '{}' ({} stories)", category, stories.size());

				// Section Title
				g2d.setColor(catColor);
				g2d.fillOval(padding, y + 8, 22, 22);

				g2d.setFont(new Font("Arial", Font.BOLD, 36));
				g2d.drawString(category.toUpperCase(Locale.ROOT), padding + 38, y + 32);

				g2d.setColor(new Color(219, 231, 245));
				g2d.fillRect(padding + 250, y + 18, contentWidth - 250, 5);

				y += 65;

				for (NewsStory story : stories) {
					storyNumber++;

					int imgX = padding + cardInnerMargin;
					int imgY = y + cardInnerMargin;

					int headlineY = imgY + imgH + imageToHeadlineGap; // Ample space between image bottom and headline
					int headlineHeight = measureWrappedTextHeight(g2d, story.headline(), headlineFont, textWidth, 46, 3);

					int summaryY = headlineY + headlineHeight + 16;
					int summaryHeight = measureWrappedTextHeight(g2d, story.summary(), summaryFont, textWidth, 42, 4);

					int metaY = summaryY + summaryHeight + 24;
					int cardHeight = (metaY + 50) - y;

					// Draw Card Background & Border
					g2d.setColor(new Color(251, 253, 255));
					g2d.fillRoundRect(padding, y, contentWidth, cardHeight, 28, 28);
					g2d.setColor(new Color(214, 229, 245));
					g2d.setStroke(new BasicStroke(2.5f));
					g2d.drawRoundRect(padding, y, contentWidth, cardHeight, 28, 28);

					// Draw Non-Stretching Aspect-Ratio Preserved Image
					BufferedImage storyImg = loadStoryImage(story.imagePath());
					if (storyImg != null) {
						Shape oldClip = g2d.getClip();
						g2d.setClip(new java.awt.geom.RoundRectangle2D.Float(imgX, imgY, imgW, imgH, 20, 20));
						drawImageProportionally(g2d, storyImg, imgX, imgY, imgW, imgH);
						g2d.setClip(oldClip);
						storyImg.flush();
					} else {
						g2d.setColor(new Color(231, 238, 247));
						g2d.fillRoundRect(imgX, imgY, imgW, imgH, 20, 20);
						g2d.setColor(new Color(85, 112, 143));
						g2d.setFont(new Font("Arial", Font.BOLD, 22));
						drawCenteredString(g2d, category.toUpperCase(), imgX, imgY, imgW, imgH);
					}

					// Number Badge on Top-Left of Image
					int badgeSize = 56;
					int badgeX = imgX + 16;
					int badgeY = imgY + 16;
					g2d.setColor(Color.WHITE);
					g2d.fillOval(badgeX - 3, badgeY - 3, badgeSize + 6, badgeSize + 6);
					g2d.setColor(catColor);
					g2d.fillOval(badgeX, badgeY, badgeSize, badgeSize);
					g2d.setColor(Color.WHITE);
					g2d.setFont(new Font("Arial", Font.BOLD, 28));
					drawCenteredString(g2d, String.valueOf(storyNumber), badgeX, badgeY, badgeSize, badgeSize);

					// Render Headline
					g2d.setColor(new Color(9, 43, 96));
					g2d.setFont(headlineFont);
					drawWrappedText(g2d, story.headline(), imgX, headlineY, textWidth, 46, 3);

					// Render Summary
					g2d.setColor(new Color(53, 75, 107));
					g2d.setFont(summaryFont);
					drawWrappedText(g2d, story.summary(), imgX, summaryY, textWidth, 42, 4);

					// Render Meta Info (Source & Date)
					g2d.setColor(new Color(75, 98, 123));
					g2d.setFont(new Font("Arial", Font.BOLD, 20));
					String srcLabel = sourceLabel(story.category()) != null ? sourceLabel(story.category()) : story.source();
					g2d.drawString(srcLabel + "  •  " + story.publishedDate(), imgX, metaY);

					y += cardHeight + 28;
				}
				y += 16;
			}

			// 3. FOOTER
			log.info("[InfographicRenderer] Drawing footer banner...");
			g2d.setColor(new Color(234, 247, 253));
			g2d.fillRoundRect(padding, y, contentWidth, 160, 28, 28);

			g2d.setColor(new Color(20, 60, 114));
			g2d.setFont(new Font("Arial", Font.BOLD, 32));
			g2d.drawString("THE DAILY 20", padding + 40, y + 50);

			g2d.setColor(new Color(75, 101, 132));
			g2d.setFont(new Font("Arial", Font.PLAIN, 20));
			g2d.drawString("Trusted sources  •  Key updates  •  One briefing", padding + 40, y + 96);

			g2d.setFont(new Font("Arial", Font.BOLD, 20));
			g2d.drawString("STAY INFORMED. STAY AHEAD.", padding + 40, y + 130);

		} finally {
			g2d.dispose();
		}

		log.info("[InfographicRenderer] Converting canvas buffer to PNG byte array...");
		try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
			ImageIO.write(canvas, "png", baos);
			return baos.toByteArray();
		} finally {
			canvas.flush();
		}
	}

	private BufferedImage loadStoryImage(String imagePath) {
		if (imagePath == null || imagePath.isBlank()) return null;
		try {
			Path path = Path.of(imagePath);
			if (!Files.isRegularFile(path)) {
				log.warn("[InfographicRenderer] Story image path is not a valid regular file: {}", imagePath);
				return null;
			}
			return ImageIO.read(path.toFile());
		} catch (Exception e) {
			log.warn("[InfographicRenderer] Failed to load story image from path '{}': {}", imagePath, e.getMessage());
			return null;
		}
	}

	private void drawImageProportionally(Graphics2D g2d, BufferedImage img, int x, int y, int targetW, int targetH) {
		int imgW = img.getWidth();
		int imgH = img.getHeight();
		if (imgW <= 0 || imgH <= 0) {
			g2d.drawImage(img, x, y, targetW, targetH, null);
			return;
		}

		double targetRatio = (double) targetW / targetH;
		double imgRatio = (double) imgW / imgH;

		int drawW, drawH, drawX, drawY;
		if (imgRatio > targetRatio) {
			drawH = targetH;
			drawW = (int) (imgRatio * targetH);
			drawX = x - (drawW - targetW) / 2;
			drawY = y;
		} else {
			drawW = targetW;
			drawH = (int) (targetW / imgRatio);
			drawX = x;
			drawY = y - (drawH - targetH) / 2;
		}
		g2d.drawImage(img, drawX, drawY, drawW, drawH, null);
	}

	private Color getCategoryColor(String category) {
		return switch (category) {
			case "World" -> new Color(11, 99, 189);
			case "India" -> new Color(232, 121, 0);
			case "Delhi NCR" -> new Color(0, 139, 127);
			case "Chandigarh" -> new Color(121, 80, 200);
			default -> new Color(11, 99, 189);
		};
	}

	private String sourceLabel(String category) {
		return switch (category) {
			case "World" -> "World News Headlines";
			case "Delhi NCR" -> "Delhi News";
			case "Chandigarh" -> "Chandigarh News";
			default -> null;
		};
	}

	private void drawCenteredString(Graphics2D g, String text, int x, int y, int width, int height) {
		FontMetrics fm = g.getFontMetrics();
		int textX = x + (width - fm.stringWidth(text)) / 2;
		int textY = y + (height - fm.getHeight()) / 2 + fm.getAscent();
		g.drawString(text, textX, textY);
	}

	private int measureWrappedTextHeight(Graphics2D g, String text, Font font, int maxWidth, int lineHeight, int maxLines) {
		if (text == null) return 0;
		FontMetrics fm = g.getFontMetrics(font);
		String[] words = text.split("\\s+");
		StringBuilder currentLine = new StringBuilder();
		int lineCount = 0;

		for (String word : words) {
			String testLine = currentLine.length() == 0 ? word : currentLine + " " + word;
			if (fm.stringWidth(testLine) > maxWidth) {
				if (lineCount >= maxLines - 1) {
					lineCount++;
					break;
				}
				currentLine = new StringBuilder(word);
				lineCount++;
			} else {
				currentLine = new StringBuilder(testLine);
			}
		}
		if (currentLine.length() > 0 && lineCount < maxLines) {
			lineCount++;
		}
		return Math.min(lineCount, maxLines) * lineHeight;
	}

	private void drawWrappedText(Graphics2D g, String text, int x, int y, int maxWidth, int lineHeight, int maxLines) {
		if (text == null) return;
		FontMetrics fm = g.getFontMetrics();
		String[] words = text.split("\\s+");
		StringBuilder currentLine = new StringBuilder();
		int lineCount = 0;

		for (String word : words) {
			String testLine = currentLine.length() == 0 ? word : currentLine + " " + word;
			if (fm.stringWidth(testLine) > maxWidth) {
				if (lineCount >= maxLines - 1) {
					g.drawString(currentLine + "...", x, y + (lineCount * lineHeight));
					return;
				}
				g.drawString(currentLine.toString(), x, y + (lineCount * lineHeight));
				currentLine = new StringBuilder(word);
				lineCount++;
			} else {
				currentLine = new StringBuilder(testLine);
			}
		}
		if (currentLine.length() > 0 && lineCount < maxLines) {
			g.drawString(currentLine.toString(), x, y + (lineCount * lineHeight));
		}
	}
}