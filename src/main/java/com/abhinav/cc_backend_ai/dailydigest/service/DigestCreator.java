
package com.abhinav.cc_backend_ai.dailydigest.service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.abhinav.cc_backend_ai.dailydigest.model.NewsDigest;
import com.abhinav.cc_backend_ai.dailydigest.model.NewsStory;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class DigestCreator {

	private static final ZoneId TIME_ZONE = ZoneId.of("Asia/Kolkata");

	private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("dd-MMM-yyyy hh.mma",
			Locale.ENGLISH);
	private static final DateTimeFormatter DATE_ONLY = DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH);

	private static final List<String> ORDER = List.of("World", "India", "Delhi NCR", "Chandigarh");

	private static final Map<String, Long> EXPECTED_COUNTS = Map.of("World", 5L, "India", 5L, "Delhi NCR", 5L,
			"Chandigarh", 5L);

	private String formatPublishedDate(String publishedAt) {
		if (publishedAt == null || publishedAt.isBlank()) {
			throw new IllegalStateException("RSS article is missing its publication date.");
		}

		try {
			return Instant.parse(publishedAt).atZone(TIME_ZONE).format(DISPLAY_DATE);
		} catch (Exception ignored) {
			// Try other common ISO date formats below.
		}

		try {
			return OffsetDateTime.parse(publishedAt).atZoneSameInstant(TIME_ZONE).format(DISPLAY_DATE);
		} catch (Exception ignored) {
			// Try a date without a time component.
		}

		try {
			return LocalDate.parse(publishedAt).format(DATE_ONLY);
		} catch (Exception ignored) {
			throw new IllegalStateException("Unrecognized RSS publication date: " + publishedAt);
		}
	}

	private void validateArticles(List<RssNewsService.Article> articles) {
		if (articles == null || articles.size() != 20) {
			throw new IllegalStateException(
					"Expected exactly 20 RSS articles; received " + (articles == null ? 0 : articles.size()));
		}

		Map<String, Long> counts = articles.stream().filter(article -> article != null)
				.collect(Collectors.groupingBy(RssNewsService.Article::category, Collectors.counting()));

		for (Map.Entry<String, Long> entry : EXPECTED_COUNTS.entrySet()) {
			long actual = counts.getOrDefault(entry.getKey(), 0L);

			if (actual != entry.getValue()) {
				throw new IllegalStateException(
						"Expected " + entry.getValue() + " articles for " + entry.getKey() + "; received " + actual);
			}
		}

		for (RssNewsService.Article article : articles) {
			if (article == null || article.category() == null || !ORDER.contains(article.category())
					|| article.title() == null || article.title().isBlank() || article.description() == null
					|| article.url() == null || !article.url().startsWith("https://") || article.source() == null
					|| article.publishedAt() == null || article.publishedAt().isBlank()) {

				throw new IllegalStateException("RSS article is missing required data.");
			}
		}
	}

	private void validateDigest(NewsDigest digest) {
		if (digest == null || digest.stories() == null || digest.stories().size() != 20) {
			throw new IllegalStateException("Digest must contain exactly 20 stories.");
		}

		Map<String, Long> actualCounts = digest.stories().stream()
				.collect(Collectors.groupingBy(NewsStory::category, Collectors.counting()));

		for (Map.Entry<String, Long> entry : EXPECTED_COUNTS.entrySet()) {
			long actual = actualCounts.getOrDefault(entry.getKey(), 0L);

			if (actual != entry.getValue()) {
				throw new IllegalStateException("Invalid story distribution for " + entry.getKey() + ": expected "
						+ entry.getValue() + ", got " + actual);
			}
		}

		Set<String> urls = new HashSet<>();

		for (NewsStory story : digest.stories()) {
			String headline = story.headline();
			String summary = story.summary();
			String url = story.url();
			String publishedDate = story.publishedDate();

			if (headline == null || headline.isBlank()) {
				throw new IllegalStateException("Invalid story: missing headline. URL=" + url);
			}

			if (summary == null) {
				throw new IllegalStateException("Invalid story: summary is null. Headline=" + headline);
			}

			String nurl = normalizeUrl(url);

			if (nurl == null || !nurl.startsWith("https://")) {
				throw new IllegalStateException("Invalid story URL. Headline=" + headline + ", URL=" + url);
			}

			if (!urls.add(nurl)) {
				throw new IllegalStateException("Duplicate story URL: " + nurl + ", Headline=" + headline);
			}

			if (publishedDate == null) {
				throw new IllegalStateException("Invalid story: publishedDate is null. Headline=" + headline);
			}
		}
	}

	public NewsDigest createDigest(List<RssNewsService.Article> articles, String date, Path tempDirectory)
			throws Exception {

		log.info("Digest generation started");

		validateArticles(articles);

		List<RssNewsService.Article> orderedArticles = articles.stream()
				.sorted(Comparator.comparingInt(article -> ORDER.indexOf(article.category()))).toList();

		List<NewsStory> stories = new ArrayList<>();

		for (int i = 0; i < orderedArticles.size(); i++) {
			RssNewsService.Article article = orderedArticles.get(i);

			String imagePath = null;

			if (article.imageUrl() != null && !article.imageUrl().isBlank()) {
				try {
					Path downloadedImage = downloadImage(article.imageUrl(), tempDirectory, i + 1);

					imagePath = downloadedImage.toAbsolutePath().toString();

				} catch (Exception ex) {
					log.error(
							"Image download failed for article: " + article.title() + ". Reason: " + ex.getMessage());
				}
			} else {
				log.error("No RSS image available for article: " + article.title());
			}

			stories.add(new NewsStory(article.category(), article.title(), article.description(), article.source(),
					formatPublishedDate(article.publishedAt()), article.url(), imagePath));
		}

		NewsDigest digest = new NewsDigest(date, stories);
		validateDigest(digest);

		log.info("Digest built successfully with " + stories.size() + " downloaded images");

		return digest;
	}

	private Path downloadImage(String imageUrl, Path destinationDirectory, int imageNumber) throws IOException {

		URI uri;

		try {
			uri = URI.create(imageUrl);
		} catch (IllegalArgumentException ex) {
			throw new IOException("Invalid RSS image URL: " + imageUrl, ex);
		}

		String scheme = uri.getScheme();

		if (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme)) {
			throw new IOException("Unsupported image URL scheme: " + imageUrl);
		}

		URLConnection connection = uri.toURL().openConnection();
		connection.setConnectTimeout(10_000);
		connection.setReadTimeout(15_000);
		connection.setRequestProperty("User-Agent", "Mozilla/5.0 DailyNewsDigest/1.0");
		connection.setRequestProperty("Accept", "image/avif,image/webp,image/png,image/jpeg,image/*");

		String contentType = connection.getContentType();

		if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
			throw new IOException(
					"RSS URL did not return an image. URL: " + imageUrl + ", Content-Type: " + contentType);
		}

		String extension = switch (contentType.split(";")[0].trim().toLowerCase(Locale.ROOT)) {
		case "image/jpeg", "image/jpg" -> ".jpg";
		case "image/png" -> ".png";
		case "image/webp" -> ".webp";
		case "image/gif" -> ".gif";
		case "image/avif" -> ".avif";
		default -> ".img";
		};

		Path destination = destinationDirectory.resolve(String.format(Locale.ROOT, "%02d%s", imageNumber, extension));

		try (InputStream input = connection.getInputStream()) {
			long bytesCopied = Files.copy(input, destination, StandardCopyOption.REPLACE_EXISTING);

			if (bytesCopied == 0) {
				Files.deleteIfExists(destination);
				throw new IOException("Downloaded image is empty: " + imageUrl);
			}
		} catch (IOException ex) {
			Files.deleteIfExists(destination);
			throw new IOException("Failed to download RSS image: " + imageUrl, ex);
		}

		return destination;
	}

	private String normalizeUrl(String url) {
		if (url == null) {
			return null;
		}

		String normalized = url.trim();

		// Convert Markdown links: [label](https://example.com)
		if (normalized.matches("^\\[[^\\]]+\\]\\(https?://[^)]+\\)$")) {
			normalized = normalized.replaceFirst("^\\[[^\\]]+\\]\\(", "").replaceFirst("\\)$", "");
		}

		// Remove URL fragments, which usually don't identify different articles
		int fragmentIndex = normalized.indexOf('#');
		if (fragmentIndex >= 0) {
			normalized = normalized.substring(0, fragmentIndex);
		}

		return normalized;
	}
}
