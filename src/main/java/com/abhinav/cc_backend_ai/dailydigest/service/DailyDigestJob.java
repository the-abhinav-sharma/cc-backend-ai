package com.abhinav.cc_backend_ai.dailydigest.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
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
	private final InfographicRenderer renderer;
	private final EmailService email;
	private final PngOptimizationService pngOptimizationService;
	@Value("${digest.timezone}")
	private String timezone;

	public DailyDigestJob(RssNewsService r, DigestCreator s, InfographicRenderer x, EmailService e,
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
			e.printStackTrace();
		}
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MMM-yyyy h.mma", Locale.ENGLISH);
		String digestDate = ZonedDateTime.now(ZoneId.of(timezone)).format(formatter);
		log.info("=================Daily digest " + digestDate + " started==================");
		try {
			var date = LocalDate.now(ZoneId.of(timezone))
					.format(DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH));
			var digest = selection.createDigest(rss.fetch(), date, tempDirectory);

			byte[] png = renderer.render(digest, digestDate);
			byte[] optimizedPng = pngOptimizationService.optimize(png, 0.70f);

			log.info("PNG size: {} MB -> {} MB", String.format("%.2f", png.length / (1024.0 * 1024.0)),
					String.format("%.2f", optimizedPng.length / (1024.0 * 1024.0)));

			email.send(digestDate, optimizedPng);

			// email.send(digest, renderer.render(digest, imageBytes));
			log.info("Daily digest sent: " + digestDate);
		} catch (Exception e) {
			log.error("Digest failed; not sent: " + e.getMessage());
			e.printStackTrace();
		} finally {
			deleteDirectory(tempDirectory);
			log.info("=================Daily digest " + digestDate + " complete==================");
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
					log.error("Failed to delete temporary file: " + path + " - " + ex.getMessage());
				}
			});
		} catch (IOException ex) {
			log.error("Failed to clean temporary directory: " + directory + " - " + ex.getMessage());
		}
	}
}