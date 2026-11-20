package com.abhinav.cc_backend_ai.dailydigest.service;

import com.abhinav.cc_backend_ai.dailydigest.model.NewsDigest;
import com.abhinav.cc_backend_ai.dailydigest.model.NewsStory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
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
	private static final int CANVAS_WIDTH = 1080;
	private static final int PADDING = 32;
	private static final int CONTENT_WIDTH = CANVAS_WIDTH - (PADDING * 2);
	private static final int CARD_INNER_MARGIN = 28;
	private static final int TEXT_WIDTH = CONTENT_WIDTH - (CARD_INNER_MARGIN * 2);
	private static final int IMG_W = CONTENT_WIDTH - (CARD_INNER_MARGIN * 2);
	private static final int IMG_H = 460;
	private static final int IMAGE_TO_HEADLINE_GAP = 45;
	private static final int CARD_SPACING = 28;

	// Subsample images above ~2.5M pixels during decode; only skip extreme >16M pixel images
	private static final long SUBSAMPLE_PIXEL_THRESHOLD = 2_500_000L;
	private static final long MAX_SAFE_PIXELS = 16_000_000L;
	private static final long MAX_FALLBACK_FILE_BYTES = 5L * 1024 * 1024;

	private final ReentrantLock renderLock = new ReentrantLock();

	@FunctionalInterface
	public interface ChunkConsumer {
		void accept(int index, String category, byte[] pngBytes) throws Exception;
	}

	/**
	 * Streams one PNG byte[] per active category chunk to a consumer so each chunk
	 * can be optimized to JPEG and released before the next chunk canvas is allocated.
	 */
	public void renderChunksStream(NewsDigest digest, String digestDate, ChunkConsumer consumer) throws Exception {
		log.info("[InfographicRenderer] Streaming chunked render requested for date: {}", digestDate);
		if (!renderLock.tryLock()) {
			log.warn("[InfographicRenderer] Render conflict: Another infographic thread is already active.");
			throw new IllegalStateException("Another infographic render is already running");
		}
		long startTime = System.currentTimeMillis();
		try {
			Map<String, List<NewsStory>> grouped = groupStoriesByCategory(digest);
			var entries = new ArrayList<>(grouped.entrySet());
			if (entries.isEmpty()) {
				return;
			}

			Font headlineFont = new Font("Arial", Font.BOLD, 36);
			Font summaryFont = new Font("Arial", Font.PLAIN, 32);
			int storyNumber = 0;

			for (int idx = 0; idx < entries.size(); idx++) {
				boolean isFirst = (idx == 0);
				boolean isLast = (idx == entries.size() - 1);
				String category = entries.get(idx).getKey();
				List<NewsStory> stories = entries.get(idx).getValue();

				int chunkHeight = calculateChunkHeight(stories, headlineFont, summaryFont, isFirst, isLast);
				log.info("[InfographicRenderer] Chunk '{}' dimensions: Width = {}, Height = {}px (first={}, last={})",
						category, CANVAS_WIDTH, chunkHeight, isFirst, isLast);

				BufferedImage chunkCanvas = new BufferedImage(CANVAS_WIDTH, chunkHeight, BufferedImage.TYPE_INT_RGB);
				Graphics2D g2d = chunkCanvas.createGraphics();
				try {
					configureQualityHints(g2d);
					g2d.setColor(new Color(237, 242, 248));
					g2d.fillRect(0, 0, CANVAS_WIDTH, chunkHeight);

					int y = isFirst ? PADDING : 0;
					if (isFirst) {
						drawHeaderBanner(g2d, digestDate, y);
						y += 300;
					}

					storyNumber = drawCategorySection(g2d, category, stories, storyNumber, headlineFont, summaryFont, y);
					y = getSectionEndY(stories, headlineFont, summaryFont, y);

					if (isLast) {
						drawFooterBanner(g2d, y + 12);
					}
				} finally {
					g2d.dispose();
				}

				byte[] pngBytes;
				try {
					pngBytes = encodePng(chunkCanvas, 4 * 1024 * 1024);
				} finally {
					chunkCanvas.flush();
				}

				consumer.accept(idx, category, pngBytes);
			}

			log.info("[InfographicRenderer] Streaming chunked render completed in {} ms",
					(System.currentTimeMillis() - startTime));
		} catch (Exception e) {
			log.error("[InfographicRenderer] Streaming chunked render failed after {} ms: {}",
					(System.currentTimeMillis() - startTime), e.getMessage(), e);
			throw e;
		} finally {
			renderLock.unlock();
		}
	}

	/**
	 * Renders the digest as one PNG byte[] per active category chunk to keep peak heap usage low.
	 * The first chunk includes the header banner; the last chunk includes the footer banner.
	 */
	public List<byte[]> renderChunks(NewsDigest digest, String digestDate) throws Exception {
		log.info("[InfographicRenderer] Chunked render execution requested for date: {}", digestDate);
		if (!renderLock.tryLock()) {
			log.warn("[InfographicRenderer] Render conflict: Another infographic thread is already active.");
			throw new IllegalStateException("Another infographic render is already running");
		}
		long startTime = System.currentTimeMillis();
		try {
			List<byte[]> chunks = generateInfographicChunks(digest, digestDate);
			long totalBytes = chunks.stream().mapToLong(b -> b.length).sum();
			log.info("[InfographicRenderer] Chunked render completed in {} ms ({} chunks, total {} bytes)",
					(System.currentTimeMillis() - startTime), chunks.size(), totalBytes);
			return chunks;
		} catch (Exception e) {
			log.error("[InfographicRenderer] Chunked render failed after {} ms: {}",
					(System.currentTimeMillis() - startTime), e.getMessage(), e);
			throw e;
		} finally {
			renderLock.unlock();
		}
	}

	/**
	 * Single-image fallback retained for compatibility if a single byte[] is ever requested.
	 */
	public byte[] render(NewsDigest digest, String digestDate) throws Exception {
		log.info("[InfographicRenderer] Render execution requested for date: {}", digestDate);
		if (!renderLock.tryLock()) {
			log.warn("[InfographicRenderer] Render conflict: Another infographic thread is already active.");
			throw new IllegalStateException("Another infographic render is already running");
		}
		long startTime = System.currentTimeMillis();
		try {
			byte[] result = generateSingleInfographicImage(digest, digestDate);
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

	private List<byte[]> generateInfographicChunks(NewsDigest digest, String digestDate) throws IOException {
		Map<String, List<NewsStory>> grouped = groupStoriesByCategory(digest);
		var entries = new ArrayList<>(grouped.entrySet());
		List<byte[]> chunks = new ArrayList<>(entries.size());

		if (entries.isEmpty()) {
			return chunks;
		}

		Font headlineFont = new Font("Arial", Font.BOLD, 36);
		Font summaryFont = new Font("Arial", Font.PLAIN, 32);

		int storyNumber = 0;

		for (int idx = 0; idx < entries.size(); idx++) {
			boolean isFirst = (idx == 0);
			boolean isLast = (idx == entries.size() - 1);
			String category = entries.get(idx).getKey();
			List<NewsStory> stories = entries.get(idx).getValue();

			int chunkHeight = calculateChunkHeight(stories, headlineFont, summaryFont, isFirst, isLast);
			log.info("[InfographicRenderer] Chunk '{}' dimensions: Width = {}, Height = {}px (first={}, last={})",
					category, CANVAS_WIDTH, chunkHeight, isFirst, isLast);

			BufferedImage chunkCanvas = new BufferedImage(CANVAS_WIDTH, chunkHeight, BufferedImage.TYPE_INT_RGB);
			Graphics2D g2d = chunkCanvas.createGraphics();
			try {
				configureQualityHints(g2d);
				g2d.setColor(new Color(237, 242, 248));
				g2d.fillRect(0, 0, CANVAS_WIDTH, chunkHeight);

				int y = isFirst ? PADDING : 0;
				if (isFirst) {
					drawHeaderBanner(g2d, digestDate, y);
					y += 300;
				}

				storyNumber = drawCategorySection(g2d, category, stories, storyNumber, headlineFont, summaryFont, y);
				y = getSectionEndY(stories, headlineFont, summaryFont, y);

				if (isLast) {
					drawFooterBanner(g2d, y + 12);
				}
			} finally {
				g2d.dispose();
			}

			try {
				byte[] pngBytes = encodePng(chunkCanvas, 4 * 1024 * 1024);
				chunks.add(pngBytes);
			} finally {
				chunkCanvas.flush();
			}
		}

		return chunks;
	}

	private byte[] generateSingleInfographicImage(NewsDigest digest, String digestDate) throws IOException {
		Map<String, List<NewsStory>> grouped = groupStoriesByCategory(digest);
		Font headlineFont = new Font("Arial", Font.BOLD, 36);
		Font summaryFont = new Font("Arial", Font.PLAIN, 32);

		var entries = new ArrayList<>(grouped.entrySet());
		int estimatedHeight = 200;
		for (int i = 0; i < entries.size(); i++) {
			boolean first = (i == 0);
			boolean last = (i == entries.size() - 1);
			estimatedHeight += calculateChunkHeight(entries.get(i).getValue(), headlineFont, summaryFont, first, last);
		}
		log.info("[InfographicRenderer] Canvas dimensions calculated: Width = {}, Height = {}px", CANVAS_WIDTH, estimatedHeight);

		BufferedImage canvas = new BufferedImage(CANVAS_WIDTH, estimatedHeight, BufferedImage.TYPE_INT_RGB);
		Graphics2D g2d = canvas.createGraphics();
		int storyNumber = 0;

		try {
			configureQualityHints(g2d);
			g2d.setColor(new Color(237, 242, 248));
			g2d.fillRect(0, 0, CANVAS_WIDTH, estimatedHeight);

			int y = PADDING;
			drawHeaderBanner(g2d, digestDate, y);
			y += 300;

			for (int i = 0; i < entries.size(); i++) {
				Map.Entry<String, List<NewsStory>> entry = entries.get(i);
				storyNumber = drawCategorySection(g2d, entry.getKey(), entry.getValue(), storyNumber, headlineFont, summaryFont, y);
				y = getSectionEndY(entry.getValue(), headlineFont, summaryFont, y);
				if (i < entries.size() - 1) {
					y += CARD_SPACING;
				}
			}

			drawFooterBanner(g2d, y + 12);
		} finally {
			g2d.dispose();
		}

		try {
			return encodePng(canvas, 12 * 1024 * 1024);
		} finally {
			canvas.flush();
		}
	}

	private Map<String, List<NewsStory>> groupStoriesByCategory(NewsDigest digest) {
		log.debug("[InfographicRenderer] Grouping stories by priority categories...");
		Map<String, List<NewsStory>> grouped = new LinkedHashMap<>();
		int totalStoriesCount = 0;
		for (String cat : ORDER) {
			List<NewsStory> stories = digest.stories().stream()
					.filter(s -> cat.equalsIgnoreCase(s.category()))
					.toList();
			if (!stories.isEmpty()) {
				grouped.put(cat, stories);
				totalStoriesCount += stories.size();
				log.debug("   -> Category '{}': {} stories found", cat, stories.size());
			}
		}
		log.info("[InfographicRenderer] Total stories grouped across categories: {}", totalStoriesCount);
		return grouped;
	}

	private int calculateChunkHeight(
			List<NewsStory> stories,
			Font headlineFont,
			Font summaryFont,
			boolean includeHeader,
			boolean includeFooter
	) {
		BufferedImage measureCanvas = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
		Graphics2D measureG2d = measureCanvas.createGraphics();
		measureG2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		measureG2d.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
		try {
			int height = includeHeader ? (PADDING + 300) : 0;
			height += getSectionHeight(measureG2d, stories, headlineFont, summaryFont);
			if (includeFooter) {
				height += 184;
			} else {
				// Trim trailing inter-card spacing so consecutive chunks join seamlessly
				height = Math.max(0, height - CARD_SPACING);
			}
			return height;
		} finally {
			measureG2d.dispose();
			measureCanvas.flush();
		}
	}

	private int getSectionHeight(Graphics2D g2d, List<NewsStory> stories, Font headlineFont, Font summaryFont) {
		int height = 65;
		for (NewsStory story : stories) {
			int headlineHeight = measureWrappedTextHeight(g2d, story.headline(), headlineFont, TEXT_WIDTH, 46, 3);
			int summaryHeight = measureWrappedTextHeight(g2d, story.summary(), summaryFont, TEXT_WIDTH, 42, 4);
			int metaHeight = 50;
			int cardHeight = (CARD_INNER_MARGIN * 2) + IMG_H + IMAGE_TO_HEADLINE_GAP + headlineHeight + 16 + summaryHeight + 24 + metaHeight;
			height += cardHeight + CARD_SPACING;
		}
		return height;
	}

	private int getSectionEndY(List<NewsStory> stories, Font headlineFont, Font summaryFont, int startY) {
		BufferedImage measureCanvas = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
		Graphics2D measureG2d = measureCanvas.createGraphics();
		try {
			return startY + getSectionHeight(measureG2d, stories, headlineFont, summaryFont);
		} finally {
			measureG2d.dispose();
			measureCanvas.flush();
		}
	}

	private void drawHeaderBanner(Graphics2D g2d, String digestDate, int y) {
		log.debug("[InfographicRenderer] Drawing header banner...");
		GradientPaint headerGradient = new GradientPaint(
				PADDING, y, new Color(7, 29, 67),
				PADDING + CONTENT_WIDTH, y + 260, new Color(6, 68, 123)
		);
		g2d.setPaint(headerGradient);
		g2d.fillRoundRect(PADDING, y, CONTENT_WIDTH, 260, 32, 32);

		g2d.setColor(new Color(124, 224, 255));
		g2d.setFont(new Font("Arial", Font.BOLD, 20));
		g2d.drawString("YOUR DAILY NEWS BRIEFING @ 8.2.8", PADDING + 40, y + 48);

		Font titleFont = new Font("Arial", Font.BOLD, 60);
		g2d.setFont(titleFont);
		String prefixText = "THE DAILY ";
		int prefixX = PADDING + 40;
		int titleY = y + 122;

		g2d.setColor(Color.WHITE);
		g2d.drawString(prefixText, prefixX, titleY);

		FontMetrics fmTitle = g2d.getFontMetrics(titleFont);
		int prefixWidth = fmTitle.stringWidth(prefixText);

		g2d.setColor(new Color(42, 196, 245));
		g2d.drawString("20", prefixX + prefixWidth, titleY);

		g2d.setColor(new Color(211, 231, 255));
		g2d.setFont(new Font("Arial", Font.PLAIN, 24));
		g2d.drawString(digestDate != null ? digestDate : LocalDate.now().format(DateTimeFormatter.ofPattern("dd-MMM-yyyy")), PADDING + 40, y + 172);

		g2d.setColor(new Color(54, 113, 167));
		g2d.setStroke(new BasicStroke(2.5f));
		g2d.drawLine(PADDING + 40, y + 200, PADDING + CONTENT_WIDTH - 40, y + 200);

		g2d.setColor(new Color(211, 231, 255));
		g2d.setFont(new Font("Arial", Font.BOLD, 18));
		g2d.drawString("WORLD   •   INDIA   •   DELHI NCR   •   CHANDIGARH", PADDING + 40, y + 236);
	}

	private int drawCategorySection(
			Graphics2D g2d,
			String category,
			List<NewsStory> stories,
			int startStoryNumber,
			Font headlineFont,
			Font summaryFont,
			int startY
	) {
		Color catColor = getCategoryColor(category);
		log.debug("[InfographicRenderer] Rendering category section: '{}' ({} stories)", category, stories.size());

		int y = startY;
		int storyNumber = startStoryNumber;

		g2d.setColor(catColor);
		g2d.fillOval(PADDING, y + 8, 22, 22);

		g2d.setFont(new Font("Arial", Font.BOLD, 36));
		g2d.drawString(category.toUpperCase(Locale.ROOT), PADDING + 38, y + 32);

		g2d.setColor(new Color(219, 231, 245));
		g2d.fillRect(PADDING + 250, y + 18, CONTENT_WIDTH - 250, 5);

		y += 65;

		for (NewsStory story : stories) {
			storyNumber++;

			int imgX = PADDING + CARD_INNER_MARGIN;
			int imgY = y + CARD_INNER_MARGIN;

			int headlineY = imgY + IMG_H + IMAGE_TO_HEADLINE_GAP;
			int headlineHeight = measureWrappedTextHeight(g2d, story.headline(), headlineFont, TEXT_WIDTH, 46, 3);

			int summaryY = headlineY + headlineHeight + 16;
			int summaryHeight = measureWrappedTextHeight(g2d, story.summary(), summaryFont, TEXT_WIDTH, 42, 4);

			int metaY = summaryY + summaryHeight + 24;
			int cardHeight = (metaY + 50) - y;

			g2d.setColor(new Color(251, 253, 255));
			g2d.fillRoundRect(PADDING, y, CONTENT_WIDTH, cardHeight, 28, 28);
			g2d.setColor(new Color(214, 229, 245));
			g2d.setStroke(new BasicStroke(2.5f));
			g2d.drawRoundRect(PADDING, y, CONTENT_WIDTH, cardHeight, 28, 28);

			log.trace("[InfographicRenderer] Loading image for story #{}: {}", storyNumber, story.imagePath());
			BufferedImage storyImg = loadAndSmoothScaleImage(story.imagePath(), IMG_W, IMG_H);
			if (storyImg != null) {
				Shape oldClip = g2d.getClip();
				g2d.setClip(new RoundRectangle2D.Float(imgX, imgY, IMG_W, IMG_H, 20, 20));
				drawImageCover(g2d, storyImg, imgX, imgY, IMG_W, IMG_H);
				g2d.setClip(oldClip);
				storyImg.flush();
			} else {
				g2d.setColor(new Color(231, 238, 247));
				g2d.fillRoundRect(imgX, imgY, IMG_W, IMG_H, 20, 20);
				g2d.setColor(new Color(85, 112, 143));
				g2d.setFont(new Font("Arial", Font.BOLD, 22));
				drawCenteredString(g2d, category.toUpperCase(), imgX, imgY, IMG_W, IMG_H);
			}

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

			g2d.setColor(new Color(9, 43, 96));
			g2d.setFont(headlineFont);
			drawWrappedText(g2d, story.headline(), imgX, headlineY, TEXT_WIDTH, 46, 3);

			g2d.setColor(new Color(53, 75, 107));
			g2d.setFont(summaryFont);
			drawWrappedText(g2d, story.summary(), imgX, summaryY, TEXT_WIDTH, 42, 4);

			g2d.setColor(new Color(75, 98, 123));
			g2d.setFont(new Font("Arial", Font.BOLD, 20));
			String srcLabel = sourceLabel(story.category()) != null ? sourceLabel(story.category()) : story.source();
			g2d.drawString(srcLabel + "  •  " + story.publishedDate(), imgX, metaY);

			y += cardHeight + CARD_SPACING;
		}

		return storyNumber;
	}

	private void drawFooterBanner(Graphics2D g2d, int y) {
		log.debug("[InfographicRenderer] Drawing footer banner...");
		g2d.setColor(new Color(234, 247, 253));
		g2d.fillRoundRect(PADDING, y, CONTENT_WIDTH, 160, 28, 28);

		g2d.setColor(new Color(20, 60, 114));
		g2d.setFont(new Font("Arial", Font.BOLD, 32));
		g2d.drawString("THE DAILY 20", PADDING + 40, y + 50);

		g2d.setColor(new Color(75, 101, 132));
		g2d.setFont(new Font("Arial", Font.PLAIN, 20));
		g2d.drawString("Trusted sources  •  Key updates  •  One briefing", PADDING + 40, y + 96);

		g2d.setFont(new Font("Arial", Font.BOLD, 20));
		g2d.drawString("STAY INFORMED. STAY AHEAD.", PADDING + 40, y + 130);
	}

	private byte[] encodePng(BufferedImage canvas, int initialBufferCapacity) throws IOException {
		log.debug("[InfographicRenderer] Converting canvas buffer to PNG byte array...");
		try (ByteArrayOutputStream baos = new ByteArrayOutputStream(initialBufferCapacity)) {
			ImageIO.write(canvas, "png", baos);
			return baos.toByteArray();
		}
	}

	private void configureQualityHints(Graphics2D g2d) {
		g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g2d.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
		g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
		g2d.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
		g2d.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
	}

	private BufferedImage loadAndSmoothScaleImage(String imagePath, int targetW, int targetH) {
		if (imagePath == null || imagePath.isBlank()) return null;
		try {
			Path path = Path.of(imagePath);
			if (!Files.isRegularFile(path)) {
				log.warn("[InfographicRenderer] Story image path is not a valid regular file: {}", imagePath);
				return null;
			}

			BufferedImage original = decodeWithSubsampling(path);
			if (original == null) {
				long fileSize = Files.size(path);
				if (fileSize <= MAX_FALLBACK_FILE_BYTES) {
					original = ImageIO.read(path.toFile());
				} else {
					log.warn("[InfographicRenderer] Skipping oversized non-subsampled image ({} bytes): {}", fileSize, imagePath);
					return null;
				}
			}
			if (original == null) return null;

			try {
				int origW = original.getWidth();
				int origH = original.getHeight();
				if (origW <= 0 || origH <= 0) {
					return null;
				}

				double ratioW = (double) targetW / origW;
				double ratioH = (double) targetH / origH;
				double ratio = Math.max(ratioW, ratioH);

				if (ratio >= 1.0) {
					BufferedImage copy = new BufferedImage(origW, origH, BufferedImage.TYPE_INT_RGB);
					Graphics2D copyG2d = copy.createGraphics();
					try {
						copyG2d.setColor(Color.WHITE);
						copyG2d.fillRect(0, 0, origW, origH);
						copyG2d.drawImage(original, 0, 0, null);
					} finally {
						copyG2d.dispose();
					}
					return copy;
				}

				int scaledW = Math.max(1, (int) Math.round(origW * ratio));
				int scaledH = Math.max(1, (int) Math.round(origH * ratio));

				BufferedImage smoothed = new BufferedImage(scaledW, scaledH, BufferedImage.TYPE_INT_RGB);
				Graphics2D g2d = smoothed.createGraphics();
				try {
					g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
					g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
					g2d.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
					g2d.drawImage(original, 0, 0, scaledW, scaledH, null);
				} finally {
					g2d.dispose();
				}
				return smoothed;
			} finally {
				original.flush();
			}
		} catch (Exception e) {
			log.warn("[InfographicRenderer] Failed to load/smooth-scale story image from path '{}': {}", imagePath, e.getMessage());
			return null;
		}
	}

	private BufferedImage decodeWithSubsampling(Path path) throws IOException {
		try (ImageInputStream iis = ImageIO.createImageInputStream(path.toFile())) {
			if (iis == null) {
				return null;
			}
			Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
			if (!readers.hasNext()) {
				return null;
			}
			ImageReader reader = readers.next();
			try {
				reader.setInput(iis, true, true);
				int w = reader.getWidth(0);
				int h = reader.getHeight(0);
				long totalPixels = (long) w * h;

				if (totalPixels > MAX_SAFE_PIXELS) {
					log.warn("[InfographicRenderer] Skipping extreme resolution image ({}x{}): {}", w, h, path);
					return null;
				}

				int step = 1;
				if (totalPixels > SUBSAMPLE_PIXEL_THRESHOLD) {
					double ratio = Math.sqrt((double) totalPixels / SUBSAMPLE_PIXEL_THRESHOLD);
					step = Math.max(2, (int) Math.ceil(ratio));
					log.debug("[InfographicRenderer] Subsampling {}x{} by {}x for memory safety: {}",
							w, h, step, path.getFileName());
				}

				ImageReadParam param = reader.getDefaultReadParam();
				if (step > 1) {
					param.setSourceSubsampling(step, step, 0, 0);
				}
				return reader.read(0, param);
			} finally {
				reader.dispose();
			}
		}
	}

	private void drawImageCover(Graphics2D g2d, BufferedImage img, int x, int y, int targetW, int targetH) {
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
			drawW = (int) (targetH * imgRatio);
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