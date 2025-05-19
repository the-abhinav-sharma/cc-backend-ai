package com.abhinav.cc_backend_ai.service;

import java.io.File;
import java.util.Calendar;
import java.util.Date;
import java.util.Properties;
import java.util.TimeZone;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.mail.BodyPart;
import jakarta.mail.Flags;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.search.AndTerm;
import jakarta.mail.search.ComparisonTerm;
import jakarta.mail.search.FlagTerm;
import jakarta.mail.search.ReceivedDateTerm;
import jakarta.mail.search.SearchTerm;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class MailService {

	@Value("${spring.mail.username}")
	private String emailUsername;

	@Value("${spring.mail.password}")
	private String emailPassword;

	@Value("${spring.mail.imap.host}")
	private String emailHostname;

	public File getImageFromGmail() {
		File file = null;
		Properties props = new Properties();
		props.put("mail.store.protocol", "imaps");

		try {
			log.info("Mail search started!");
			Session session = Session.getInstance(props, null);
			Store store = session.getStore();
			store.connect(emailHostname, emailUsername, emailPassword);
			
			if(store.isConnected()) {
				log.info("Mail Connection Successful!");
			}else {
				log.info("Mail Connection Unsuccessful!");
			}

			Folder inbox = store.getFolder("INBOX");
			inbox.open(Folder.READ_WRITE);

			Flags seen = new Flags(Flags.Flag.SEEN);
			FlagTerm unseenFlagTerm = new FlagTerm(seen, false);

			Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata"));
			cal.set(Calendar.HOUR_OF_DAY, 0);
			cal.set(Calendar.MINUTE, 0);
			cal.set(Calendar.SECOND, 0);
			cal.set(Calendar.MILLISECOND, 0);
			Date todayStart = cal.getTime();

			ReceivedDateTerm receivedToday = new ReceivedDateTerm(ComparisonTerm.GE, todayStart);
			SearchTerm searchTerm = new AndTerm(unseenFlagTerm, receivedToday);

			Message[] messages = inbox.search(searchTerm);
			if (messages.length == 0) {
				log.info("No unread mails for today!");
			}

			for (Message message : messages) {

				String subject = message.getSubject();
				if (subject != null && subject.startsWith("CC-")) {

					if (message.isMimeType("multipart/*")) {

						Multipart multipart = (Multipart) message.getContent();
						for (int i = 0; i < multipart.getCount(); i++) {

							BodyPart part = multipart.getBodyPart(i);

							if (Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition())
									&& (part.getContentType().startsWith("image/")
											|| part.getContentType().startsWith("IMAGE/"))) {

								MimeBodyPart mimePart = (MimeBodyPart) part;
								file = new File(System.getProperty("java.io.tmpdir"), mimePart.getFileName());
								mimePart.saveFile(file);
								log.info("Image extraction from mail successful : " + file.getName());

							}
						}
					}
					message.setFlag(Flags.Flag.SEEN, true); // Mark as read
				} else {
					log.info("No CC mails present in mailbox!");
				}
				break;
			}
			inbox.close(false);
			store.close();

		} catch (Exception e) {
			e.printStackTrace();
		}
		return file;
	}
}
