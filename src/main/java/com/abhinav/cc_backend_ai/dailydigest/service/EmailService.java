package com.abhinav.cc_backend_ai.dailydigest.service;

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
	private final JavaMailSender mail;
	@Value("${digest.recipient}")
	private String[] recipients;
	@Value("${digest.sender}")
	private String from;

	public EmailService(JavaMailSender mail) {
		this.mail = mail;
	}

	public void send(String digestDate, byte[] png) throws Exception {
		log.info("Mail started");

		if (png == null || png.length == 0) {
			throw new IllegalArgumentException("Generated PNG image is empty.");
		}

		// Log generated image size
		log.info("Infographic size: {} bytes ({} KB, {} MB)", String.format("%,d", png.length),
				String.format("%.2f", png.length / 1024.0), String.format("%.2f", png.length / (1024.0 * 1024.0)));

		MimeMessage m = mail.createMimeMessage();
		MimeMessageHelper h = new MimeMessageHelper(m, true, "UTF-8");

		h.setFrom(from);
		h.setBcc(recipients);
		h.setSubject("[The Daily 20] : " + digestDate);

		String body = "<div style=\"background-color: #f4f4f4; padding: 20px 0; text-align: center;\">"
				+ "<table role=\"presentation\" border=\"0\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin: 0 auto; max-width: 680px; width: 100%; background-color: #ffffff;\">"
				+ "<tr>" + "<td style=\"padding: 10px; text-align: center;\">"
				+ "<a href=\"javascript:void(0);\" style=\"display: block; text-decoration: none; cursor: default;\">"
				+ "<img src='cid:daily5' style='display: block; width: 100%; max-width: 680px; height: auto; max-height: none; border: 0; outline: none; text-decoration: none;' alt='The Daily 20'>"
				+ "</a>" + "</td>" + "</tr>" + "</table>" + "</div>";

		h.setText(body, true);

		h.addInline("daily5", new ByteArrayResource(png), "image/png");

		log.info("Before sending mail");

		long start = System.nanoTime();
		mail.send(m);
		long elapsedMs = (System.nanoTime() - start) / 1_000_000;

		log.info("Mail sent");
		log.info("mail.send() duration: " + elapsedMs + " ms");
	}

}