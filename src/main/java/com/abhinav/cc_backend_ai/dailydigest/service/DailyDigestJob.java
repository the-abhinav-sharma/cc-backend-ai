package com.abhinav.cc_backend_ai.dailydigest.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class DailyDigestJob {
	private final RssNewsService rss;
	private final DigestCreator selection;
	private final PureJavaInfographicRenderer renderer;
	private final EmailService email;
	private final PngOptimizationService pngOptimizationService;
	@Value("${digest.timezone}")
	private String timezone;

	public DailyDigestJob(RssNewsService r, DigestCreator s, PureJavaInfographicRenderer x, EmailService e,
			PngOptimizationService p) {
		rss = r;
		selection = s;
		renderer = x;
		email = e;
		pngOptimizationService = p;
	}

	@Scheduled(cron = "${digest.cron}", zone = "${digest.timezone}")
	public void runDaily() {
		Path tempDirectory = null;
		try {
			tempDirectory = Files.createTempDirectory("daily20-");
		} catch (IOException e) {
			log.error("Failed to create temp directory: {}", e.getMessage(), e);
		}
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MMM-yyyy h.mma", Locale.ENGLISH);
		String digestDate = ZonedDateTime.now(ZoneId.of(timezone)).format(formatter);
		log.info("=================Daily digest {} started==================", digestDate);
		try {
			var date = LocalDate.now(ZoneId.of(timezone))
					.format(DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH));
			var digest = selection.createDigest(rss.fetch(), date, tempDirectory);

			List<byte[]> rawChunks = renderer.renderChunks(digest, digestDate);
			List<byte[]> optimizedChunks = new ArrayList<>(rawChunks.size());

			long rawTotalBytes = 0;
			long optTotalBytes = 0;

			for (int i = 0; i < rawChunks.size(); i++) {
				byte[] rawChunk = rawChunks.get(i);
				byte[] optChunk = pngOptimizationService.optimize(rawChunk, 0.70f);
				optimizedChunks.add(optChunk);

				rawTotalBytes += rawChunk.length;
				optTotalBytes += optChunk.length;

				log.info("Chunk {} PNG size: {} MB -> {} MB",
						i,
						String.format("%.2f", rawChunk.length / (1024.0 * 1024.0)),
						String.format("%.2f", optChunk.length / (1024.0 * 1024.0)));
			}

			log.info("Total PNG size across {} chunks: {} MB -> {} MB",
					optimizedChunks.size(),
					String.format("%.2f", rawTotalBytes / (1024.0 * 1024.0)),
					String.format("%.2f", optTotalBytes / (1024.0 * 1024.0)));

			email.send(digestDate, optimizedChunks);
			log.info("Daily digest sent: {}", digestDate);
		} catch (Exception e) {
			log.error("Digest failed; not sent: {}", e.getMessage(), e);
		} finally {
			deleteDirectory(tempDirectory);
			log.info("=================Daily digest {} complete==================", digestDate);
		}
	}

	private void deleteDirectory(Path directory) {
		if (directory == null || !Files.exists(directory)) {
			return;
		}

		try (var paths = Files.walk(directory)) {
			paths.sorted(Comparator.reverseOrder()).forEach(path -> {
				try {
					Files.deleteIfExists(path);
				} catch (IOException ex) {
					log.error("Failed to delete temporary file: {} - {}", path, ex.getMessage());
				}
			});
		} catch (IOException ex) {
			log.error("Failed to clean temporary directory: {} - {}", directory, ex.getMessage());
		}
	}
}