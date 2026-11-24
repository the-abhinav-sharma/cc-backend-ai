package com.abhinav.cc_backend_ai.dailydigest.service;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class EmailService {
	private static final DateTimeFormatter DATE_FORMATTER =
			DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

	private final JavaMailSender mail;
	@Value("${digest.recipient}")
	private String[] recipients;
	@Value("${digest.sender}")
	private String from;
	@Value("${digest.timezone}")
	private String timezone;

	public EmailService(JavaMailSender mail) {
		this.mail = mail;
	}

	public void send(String digestDate, List<byte[]> infographicChunks) throws Exception {
		log.info("Mail started");

		if (recipients == null || recipients.length == 0) {
			throw new IllegalArgumentException("No digest recipients configured.");
		}
		if (infographicChunks == null || infographicChunks.isEmpty()) {
			throw new IllegalArgumentException("Generated PNG infographic chunks are empty.");
		}

		long totalBytes = 0;
		List<ByteArrayResource> chunkResources = new ArrayList<>(infographicChunks.size());
		for (int i = 0; i < infographicChunks.size(); i++) {
			byte[] chunk = infographicChunks.get(i);
			if (chunk == null || chunk.length == 0) {
				throw new IllegalArgumentException("Infographic chunk at index " + i + " is empty.");
			}
			totalBytes += chunk.length;
			chunkResources.add(new ByteArrayResource(chunk));
			log.info("Chunk {} size: {} bytes ({} KB, {} MB)",
					i,
					String.format("%,d", chunk.length),
					String.format("%.2f", chunk.length / 1024.0),
					String.format("%.2f", chunk.length / (1024.0 * 1024.0)));
		}

		log.info("Total infographic size across {} chunks: {} bytes ({} KB, {} MB)",
				infographicChunks.size(),
				String.format("%,d", totalBytes),
				String.format("%.2f", totalBytes / 1024.0),
				String.format("%.2f", totalBytes / (1024.0 * 1024.0)));

		String subject = getDigestSubject();
		String body = buildHtmlBody(infographicChunks.size());
		List<MimeMessage> messages = new ArrayList<>(recipients.length);

		for (String recipient : recipients) {
			if (recipient == null || recipient.isBlank()) {
				continue;
			}
			MimeMessage m = mail.createMimeMessage();
			MimeMessageHelper h = new MimeMessageHelper(m, true, "UTF-8");

			h.setFrom(from, "THE DAILY 20");
			h.setTo(recipient.trim());
			h.setSubject(subject);
			h.setText(body, true);

			for (int i = 0; i < chunkResources.size(); i++) {
				h.addInline("chunk" + i, chunkResources.get(i), "image/jpeg");
			}

			messages.add(m);
		}

		if (messages.isEmpty()) {
			throw new IllegalArgumentException("All configured digest recipients are blank.");
		}

		log.info("Before sending mail to {} recipients", messages.size());

		long start = System.nanoTime();
		mail.send(messages.toArray(new MimeMessage[0]));
		long elapsedMs = (System.nanoTime() - start) / 1_000_000;

		log.info("Mail sent to {} recipients", messages.size());
		log.info("mail.send() duration: " + elapsedMs + " ms");
	}

	private String buildHtmlBody(int chunkCount) {
		StringBuilder rowsHtml = new StringBuilder();
		for (int i = 0; i < chunkCount; i++) {
			String cid = "chunk" + i;
			String tdPadding = (i == 0)
					? "padding: 10px 10px 0 10px;"
					: (i == chunkCount - 1)
							? "padding: 0 10px 10px 10px;"
							: "padding: 0 10px 0 10px;";

			rowsHtml.append("<tr>")
					.append("<td style=\"").append(tdPadding).append(" text-align: center; font-size: 0; line-height: 0; mso-line-height-rule: exactly;\">")
					.append("<a href=\"javascript:void(0);\" style=\"display: block; margin: 0; padding: 0; text-decoration: none; cursor: default;\">")
					.append("<img src=\"cid:").append(cid).append("\" ")
					.append("style=\"display: block; width: 100%; max-width: 660px; height: auto; max-height: none; border: 0; outline: none; text-decoration: none; margin: 0; padding: 0;\" ")
					.append("alt=\"The Daily 20 - Part ").append(i + 1).append("\">")
					.append("</a>")
					.append("</td>")
					.append("</tr>");
		}

		return "<div style=\"background-color: #f4f4f4; padding: 20px 0; text-align: center;\">"
				+ "<table role=\"presentation\" border=\"0\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin: 0 auto; max-width: 680px; width: 100%; background-color: #ffffff;\">"
				+ rowsHtml
				+ "</table>"
				+ "</div>";
	}

	private String getDigestSubject() {
		ZonedDateTime now = ZonedDateTime.now(ZoneId.of(timezone));
		String formattedDate = now.format(DATE_FORMATTER);

		int hour = now.getHour();
		String slotLabel = hour < 11 ? "8 AM Edition"
				: hour < 17 ? "2 PM Edition"
				: "8 PM Edition";

		return slotLabel + " • " + formattedDate;
	}
}