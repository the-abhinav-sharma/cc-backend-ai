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
		log.info("Pure Java 2D Infographic Render start");
		if (!renderLock.tryLock()) {
			throw new IllegalStateException("Another infographic render is already running");
		}
		try {
			return generateInfographicImage(digest, digestDate);
		} finally {
			renderLock.unlock();
		}
	}

	private byte[] generateInfographicImage(NewsDigest digest, String digestDate) throws IOException {
		int width = 780; // Scaled 2x for crisp rendering (390px layout width * 2)
		int padding = 20;
		int contentWidth = width - (padding * 2);

		// Group stories by category
		Map<String, List<NewsStory>> grouped = new LinkedHashMap<>();
		for (String cat : ORDER) {
			List<NewsStory> stories = digest.stories().stream()
					.filter(s -> cat.equalsIgnoreCase(s.category()))
					.toList();
			if (!stories.isEmpty()) {
				grouped.put(cat, stories);
			}
		}

		// Calculate total required canvas height dynamically (no unused variables)
		int estimatedHeight = 350; // Header banner
		for (Map.Entry<String, List<NewsStory>> entry : grouped.entrySet()) {
			estimatedHeight += 60; // Section title
			estimatedHeight += entry.getValue().size() * 185; // Cards height estimate
		}
		estimatedHeight += 160; // Footer

		BufferedImage canvas = new BufferedImage(width, estimatedHeight, BufferedImage.TYPE_INT_RGB);
		Graphics2D g2d = canvas.createGraphics();

		try {
			g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

			// Background fill
			g2d.setColor(new Color(237, 242, 248));
			g2d.fillRect(0, 0, width, estimatedHeight);

			int y = padding;

			// 1. HEADER BANNER
			g2d.setColor(new Color(7, 29, 67));
			g2d.fillRoundRect(padding, y, contentWidth, 230, 24, 24);

			g2d.setColor(new Color(124, 224, 255));
			g2d.setFont(new Font("Arial", Font.BOLD, 20));
			g2d.drawString("YOUR DAILY NEWS BRIEFING @ 8.2.8", padding + 36, y + 45);

			g2d.setColor(Color.WHITE);
			g2d.setFont(new Font("Arial", Font.BOLD, 46));
			g2d.drawString("THE DAILY ", padding + 36, y + 105);
			g2d.setColor(new Color(42, 196, 245));
			g2d.drawString("20", padding + 275, y + 105);

			g2d.setColor(new Color(211, 231, 255));
			g2d.setFont(new Font("Arial", Font.PLAIN, 26));
			g2d.drawString(digestDate != null ? digestDate : LocalDate.now().format(DateTimeFormatter.ofPattern("dd-MMM-yyyy")), padding + 36, y + 150);

			g2d.setColor(new Color(54, 113, 167));
			g2d.drawLine(padding + 36, y + 175, padding + contentWidth - 36, y + 175);

			g2d.setColor(new Color(211, 231, 255));
			g2d.setFont(new Font("Arial", Font.BOLD, 18));
			g2d.drawString("WORLD  •  INDIA  •  DELHI NCR  •  CHANDIGARH", padding + 36, y + 208);

			y += 250;

			int storyNumber = 0;

			// 2. SECTIONS & CARDS
			for (Map.Entry<String, List<NewsStory>> entry : grouped.entrySet()) {
				String category = entry.getKey();
				List<NewsStory> stories = entry.getValue();

				Color catColor = getCategoryColor(category);

				// Section Title
				g2d.setColor(catColor);
				g2d.fillOval(padding, y + 8, 16, 16);

				g2d.setFont(new Font("Arial", Font.BOLD, 26));
				g2d.drawString(category.toUpperCase(Locale.ROOT), padding + 30, y + 24);

				g2d.setColor(new Color(219, 231, 245));
				g2d.fillRect(padding + 220, y + 14, contentWidth - 220, 4);

				y += 50;

				for (NewsStory story : stories) {
					storyNumber++;

					// Card Background Box
					g2d.setColor(new Color(251, 253, 255));
					g2d.fillRoundRect(padding, y, contentWidth, 170, 20, 20);
					g2d.setColor(new Color(214, 229, 245));
					g2d.drawRoundRect(padding, y, contentWidth, 170, 20, 20);

					// Number Badge
					g2d.setColor(catColor);
					g2d.fillOval(padding + 12, y + 12, 36, 36);
					g2d.setColor(Color.WHITE);
					g2d.setFont(new Font("Arial", Font.BOLD, 18));
					drawCenteredString(g2d, String.valueOf(storyNumber), padding + 12, y + 12, 36, 36);

					// Story Image or Placeholder
					int imgX = padding + 60;
					int imgY = y + 20;
					int imgW = 130;
					int imgH = 130;

					BufferedImage storyImg = loadStoryImage(story.imagePath());
					if (storyImg != null) {
						g2d.drawImage(storyImg, imgX, imgY, imgW, imgH, null);
						storyImg.flush();
					} else {
						g2d.setColor(new Color(231, 238, 247));
						g2d.fillRoundRect(imgX, imgY, imgW, imgH, 12, 12);
						g2d.setColor(new Color(85, 112, 143));
						g2d.setFont(new Font("Arial", Font.BOLD, 14));
						drawCenteredString(g2d, category.toUpperCase(), imgX, imgY, imgW, imgH);
					}

					// Headline & Summary Text Block
					int textX = imgX + imgW + 20;
					int textWidth = contentWidth - (textX - padding) - 20;

					g2d.setColor(new Color(9, 43, 96));
					g2d.setFont(new Font("Arial", Font.BOLD, 20));
					drawWrappedText(g2d, story.headline(), textX, y + 30, textWidth, 24, 2);

					g2d.setColor(new Color(53, 75, 107));
					g2d.setFont(new Font("Arial", Font.PLAIN, 15));
					drawWrappedText(g2d, story.summary(), textX, y + 85, textWidth, 20, 2);

					// Meta info (Source & Date)
					g2d.setColor(new Color(98, 116, 139));
					g2d.setFont(new Font("Arial", Font.PLAIN, 13));
					String srcLabel = sourceLabel(story.category()) != null ? sourceLabel(story.category()) : story.source();
					g2d.drawString(srcLabel + "  •  " + story.publishedDate(), textX, y + 145);

					y += 185;
				}
				y += 15;
			}

			// 3. FOOTER
			g2d.setColor(new Color(234, 247, 253));
			g2d.fillRoundRect(padding, y, contentWidth, 110, 20, 20);

			g2d.setColor(new Color(20, 60, 114));
			g2d.setFont(new Font("Arial", Font.BOLD, 22));
			g2d.drawString("THE DAILY 20", padding + 25, y + 36);

			g2d.setColor(new Color(75, 101, 132));
			g2d.setFont(new Font("Arial", Font.PLAIN, 15));
			g2d.drawString("Trusted sources  •  Key updates  •  One briefing", padding + 25, y + 65);

			g2d.setFont(new Font("Arial", Font.BOLD, 15));
			g2d.drawString("STAY INFORMED. STAY AHEAD.", padding + 25, y + 92);

		} finally {
			g2d.dispose();
		}

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
			if (!Files.isRegularFile(path)) return null;
			return ImageIO.read(path.toFile());
		} catch (Exception e) {
			return null;
		}
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
					currentLine.append("...");
					break;
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