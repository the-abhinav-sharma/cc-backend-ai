package com.abhinav.cc_backend_ai.dailydigest.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLConnection;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.rometools.rome.feed.synd.SyndContent;
import com.rometools.rome.feed.synd.SyndEnclosure;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class RssNewsService {

	@Value("${digest.rss-feeds}")
	private List<String> feeds;

	private static final List<String> CATEGORIES = List.of("World", "India", "Delhi NCR", "Chandigarh");

	public List<Article> fetch() {
		List<Article> all = new ArrayList<>();

		for (int i = 0; i < feeds.size(); i++) {
			String feedUrl = feeds.get(i);

			if (i >= CATEGORIES.size()) {
				log.error("No category configured for feed: " + feedUrl);
				continue;
			}

			String category = CATEGORIES.get(i);

			try {
				// Download each feed only once.
				byte[] xml = downloadFeed(feedUrl);

				// Parse the same XML with Jsoup to extract Tribune image fields.
				Map<String, String> imageUrls = extractImageMap(xml);

				// Parse the same XML with Rome to preserve existing article handling.
				SyndFeed feed;

				try (InputStream input = new ByteArrayInputStream(xml)) {
					feed = new SyndFeedInput().build(new XmlReader(input));
				}

				for (SyndEntry entry : feed.getEntries()) {
					String title = clean(entry.getTitle());
					String url = entry.getLink() == null ? "" : entry.getLink().trim();

					if (title.isBlank() || !url.startsWith("https://")) {
						continue;
					}

					String description = entry.getDescription() == null ? "" : clean(entry.getDescription().getValue());

					String publishedAt = entry.getPublishedDate() == null ? ""
							: entry.getPublishedDate().toInstant().toString();

					// First try Rome's normal image extraction.
					String imageUrl = extractThumbnail(entry);

					// If Rome has no image, use the image extracted from
					// the already-downloaded XML.
					if (imageUrl == null) {
						imageUrl = imageUrls.get(normalizeUrl(url));
					}

					Article article = new Article(category, title, description, url, clean(feed.getTitle()),
							publishedAt, imageUrl);

					all.add(article);
				}

				log.info("RSS fetched: " + feedUrl + " (" + feed.getEntries().size() + " entries)");

			} catch (Exception ex) {
				log.error("RSS failed: " + feedUrl + " : " + ex.getMessage());
			}
		}

		Map<String, Article> unique = all.stream()
				.collect(Collectors.toMap(a -> normalizeUrl(a.url()), Function.identity(), (a, b) -> {
					if ("Delhi NCR".equals(a.category()))
						return a;
					if ("Delhi NCR".equals(b.category()))
						return b;
					if ("Chandigarh".equals(a.category()))
						return a;
					if ("Chandigarh".equals(b.category()))
						return b;
					return compareDates(a, b) >= 0 ? a : b;
				}, LinkedHashMap::new));

		Map<String, List<Article>> byCategory = unique.values().stream()
				.collect(Collectors.groupingBy(Article::category));

		List<Article> candidates = new ArrayList<>();

		for (String category : CATEGORIES) {
			List<Article> latestFive = byCategory.getOrDefault(category, List.of()).stream()
					.filter(a -> !a.publishedAt().isBlank()).sorted((a, b) -> compareDates(b, a)).limit(5).toList();

			candidates.addAll(latestFive);

			log.info(category + " latest articles: " + latestFive.size());
		}

		log.info("Total LLM candidates: " + candidates.size());

		return candidates;
	}

	/**
	 * Downloads a feed once and returns its raw XML bytes.
	 */
	private byte[] downloadFeed(String feedUrl) throws Exception {
		URLConnection connection = URI.create(feedUrl).toURL().openConnection();

		connection.setConnectTimeout(10_000);
		connection.setReadTimeout(15_000);

		connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
				+ "AppleWebKit/537.36 (KHTML, like Gecko) " + "Chrome/131.0.0.0 Safari/537.36");

		connection.setRequestProperty("Accept",
				"application/rss+xml, application/atom+xml, " + "application/xml, text/xml, */*");

		try (InputStream input = connection.getInputStream()) {
			return input.readAllBytes();
		}
	}

	/**
	 * Extracts image URLs from the raw RSS XML, indexed by article URL. Supports
	 * Tribune's custom thumbimage/fullimage elements.
	 * 
	 * @throws IOException
	 */
	private Map<String, String> extractImageMap(byte[] xml) throws IOException {
		Map<String, String> imageMap = new LinkedHashMap<>();

		Document document = Jsoup.parse(new ByteArrayInputStream(xml), null, "", Parser.xmlParser());

		for (Element item : document.select("item")) {
			Element linkElement = item.selectFirst("link");

			if (linkElement == null) {
				continue;
			}

			String articleUrl = linkElement.text().trim();

			if (articleUrl.isBlank()) {
				continue;
			}

			String imageUrl = null;

			for (String tag : List.of("thumbimage", "fullimage")) {
				Element imageElement = item.selectFirst(tag);

				if (imageElement == null) {
					continue;
				}

				// Get direct text only; don't include nested caption/credit.
				String candidate = imageElement.ownText().trim();

				if (isImageUrl(candidate)) {
					imageUrl = candidate;
					break;
				}

				// Also support feeds that store the URL in an attribute.
				for (org.jsoup.nodes.Attribute attribute : imageElement.attributes()) {

					if (isImageUrl(attribute.getValue())) {
						imageUrl = attribute.getValue().trim();
						break;
					}
				}

				if (imageUrl != null) {
					break;
				}
			}

			if (imageUrl != null) {
				imageMap.put(normalizeUrl(articleUrl), imageUrl);
			}
		}

		log.info("Images extracted from raw RSS XML: " + imageMap.size());

		return imageMap;
	}

	private String extractThumbnail(SyndEntry entry) {
		List<org.jdom2.Element> elements = entry.getForeignMarkup();

		String imageUrl = extractCustomImage(elements, "thumbimage");

		if (imageUrl != null) {
			return imageUrl;
		}

		// Media RSS: media:thumbnail and media:content.
		for (org.jdom2.Element element : elements) {
			String name = element.getName();
			String namespace = element.getNamespaceURI();

			if ("http://search.yahoo.com/mrss/".equals(namespace)
					&& ("thumbnail".equalsIgnoreCase(name) || "content".equalsIgnoreCase(name))) {

				imageUrl = element.getAttributeValue("url");

				if (isImageUrl(imageUrl)) {
					return imageUrl.trim();
				}
			}
		}

		// RSS enclosures.
		for (SyndEnclosure enclosure : entry.getEnclosures()) {
			String type = enclosure.getType();
			String enclosureUrl = enclosure.getUrl();

			if (type != null && type.toLowerCase(Locale.ROOT).startsWith("image/") && isImageUrl(enclosureUrl)) {
				return enclosureUrl.trim();
			}
		}

		// Image in RSS description.
		if (entry.getDescription() != null) {
			imageUrl = extractImageFromHtml(entry.getDescription().getValue());

			if (isImageUrl(imageUrl)) {
				return imageUrl;
			}
		}

		// Images in RSS/Atom content.
		if (entry.getContents() != null) {
			for (SyndContent content : entry.getContents()) {
				imageUrl = extractImageFromHtml(content.getValue());

				if (isImageUrl(imageUrl)) {
					return imageUrl;
				}
			}
		}

		// Tribune full image as the final Rome-based fallback.
		return extractCustomImage(elements, "fullimage");
	}

	private String extractCustomImage(List<org.jdom2.Element> elements, String targetName) {

		for (org.jdom2.Element element : elements) {
			if (!targetName.equalsIgnoreCase(element.getName())) {
				continue;
			}

			String value = element.getTextNormalize();

			if (isImageUrl(value)) {
				return value.trim();
			}

			for (org.jdom2.Attribute attribute : element.getAttributes()) {
				if (isImageUrl(attribute.getValue())) {
					return attribute.getValue().trim();
				}
			}
		}

		return null;
	}

	private String extractImageFromHtml(String html) {
		if (html == null || html.isBlank()) {
			return null;
		}

		Document document = Jsoup.parse(html);

		for (Element img : document.select("img")) {
			String[] attributes = { "src", "data-src", "data-original", "data-lazy-src", "srcset" };

			for (String attribute : attributes) {
				String value = img.attr(attribute).trim();

				if (value.isBlank()) {
					continue;
				}

				if ("srcset".equals(attribute)) {
					value = value.split(",")[0].trim().split("\\s+")[0];
				}

				value = Parser.unescapeEntities(value, false);

				if (isImageUrl(value)) {
					return value;
				}
			}
		}

		return null;
	}

	private boolean isImageUrl(String value) {
		if (value == null || value.isBlank()) {
			return false;
		}

		String url = Parser.unescapeEntities(value.trim(), false);

		try {
			URI uri = URI.create(url);
			String scheme = uri.getScheme();

			return ("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme)) && uri.getHost() != null
					&& !uri.getHost().isBlank();

		} catch (IllegalArgumentException ex) {
			return false;
		}
	}

	private int compareDates(Article a, Article b) {
		return safeDate(a.publishedAt()).compareTo(safeDate(b.publishedAt()));
	}

	private Instant safeDate(String value) {
		try {
			return value == null || value.isBlank() ? Instant.EPOCH : Instant.parse(value);
		} catch (Exception ex) {
			return Instant.EPOCH;
		}
	}

	private String normalizeUrl(String url) {
		String normalized = url == null ? "" : url.trim();

		int queryIndex = normalized.indexOf('?');

		if (queryIndex >= 0) {
			normalized = normalized.substring(0, queryIndex);
		}

		while (normalized.endsWith("/") && normalized.length() > 8) {
			normalized = normalized.substring(0, normalized.length() - 1);
		}

		return normalized.toLowerCase(Locale.ROOT);
	}

	private String clean(String value) {
		if (value == null) {
			return "";
		}

		String cleaned = value.replaceAll("<[^>]*>", " ").replaceAll("\\s+", " ").trim();

		return Parser.unescapeEntities(cleaned, false);
	}

	public record Article(String category, String title, String description, String url, String source,
			String publishedAt, String imageUrl) {
	}
}