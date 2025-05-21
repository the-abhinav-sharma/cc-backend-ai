package com.abhinav.cc_backend_ai.service;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.model.Media;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeType;

import com.abhinav.cc_backend_ai.model.AIMaster;
import com.abhinav.cc_backend_ai.model.Answer;
import com.abhinav.cc_backend_ai.model.Question;
import com.abhinav.cc_backend_ai.repository.AIMasterRepository;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class OpenAIService {
	
	@Value("#{${codeNameMap}}")
	private Map<String, String> codeNameMapping;

	private ChatClient chatClient;
	private AIMasterRepository aiMasterRepository;

	public OpenAIService(ChatClient.Builder chatClientBuilder, AIMasterRepository aiMasterRepository) {
		this.chatClient = chatClientBuilder.build();
		this.aiMasterRepository = aiMasterRepository;
	}

	public Answer getAnswer(Question question) {
		ZoneId asiaKolkata = ZoneId.of("Asia/Kolkata");
		ZonedDateTime startTime = ZonedDateTime.ofInstant(Instant.now(), asiaKolkata);
		ChatResponse response = chatClient.prompt().user(question.question()).call().chatResponse();
		ZonedDateTime stopTime = ZonedDateTime.ofInstant(Instant.now(), asiaKolkata);
		long timeTaken = Duration.between(startTime, stopTime).toMillis();
		log.info("For prompt: " + question.question() + ", response took: " + timeTaken + " milliseconds");

		String respText = response.getResult().getOutput().getText();

		if (respText.length() > 1000) {
			respText = respText.substring(0, 1000);
		}

		aiMasterRepository.save(AIMaster.builder().prompt(question.question()).answer(respText)
				.timeIn(String.valueOf(startTime)).timeOut(String.valueOf(stopTime)).respTime(timeTaken).build());

		return new Answer(response.getResult().getOutput().getText());
	}

//	public void getPromptsByDate() {
//		String date = new SimpleDateFormat("yyyy-MM-dd").format(new java.util.Date());
//		mailService.sendEmail("Daily Prompts Report - "+new SimpleDateFormat("dd-MMM-yyyy").format(new java.util.Date()), getPromptsMailBody(
//				aiMasterRepository.findBytimeInContains(date).stream().map(ai -> ">> " + ai.getPrompt()).toList()));
//	}

	public String getPromptsMailBody(List<String> list) {
		StringBuffer sb = new StringBuffer();
		for (String prompt : list) {
			sb.append(prompt);
			sb.append(System.lineSeparator());
		}

		if (sb.length() == 0) {
			sb.append("No prompts for today");
		}

		return sb.toString();
	}

	public String extractDataFromImage(String imagePath) throws IOException {
		ClassPathResource imageResource = new ClassPathResource(imagePath);

		String prompt = "Extract the following fields and return JSON in the below format. stmtDate is Statement Generation Date. "
				+ "Just return a valid json and nothing else. Dont return any unexpected charater or token in response. JSON.stringyfy(json) the response to check if its valid: " +
                "{"
                + "  \"name\": \"\",\n"
                + "  \"minAmt\": ,\n"
                + "  \"totalAmt\": ,\n"
                + "  \"stmtDate\": \"YYYY-MM-dd\",\n"
                + "  \"dueDate\": \"YYYY-MM-dd\",\n"
                + "  \"payDate\": \null,\n"
                + "  \"currentStatus\": \"Bill Generated\",\n"
                + "  \"remarks\": \"\",\n"
                + "  \"createdOn\": \"\",\n"
                + "  \"modifiedOn\": \"\""
                + "}";

		UserMessage userMessage = new UserMessage(prompt, new Media(MimeType.valueOf("image/jpeg"), imageResource));
		return chatClient.prompt().messages(userMessage).call().chatResponse().getResult().getOutput().getText();
	}
	
	public String extractDataFromImage(String subject, File file) throws IOException {
		Resource fileResource = new FileSystemResource(file);
		log.info(codeNameMapping.toString());
		String prompt = "Extract the following fields and return JSON in the below format. stmtMonthYear is of the format MMYYYY. 01 being January and 12 being December for MM."
				+ " stmtDate is Statement Generation Date. code will be "+codeNameMapping.get(subject)+". Do not read or store any sensitive data like credit card number etc. "
				+ " Round off the decimal amount to next integer value. Just return a valid json and nothing else. Dont return any unexpected charater or token in response. JSON.stringyfy(json) the response to check if its valid: " +
                "{\"key\":"
                + "{"
                + "\"code\":\"\","
                + "\"stmtMonthYear\":\"MMYYYY\""
                + "},"
                + "\"name\":\"\","
                + "\"minAmt\":,"
                + "\"totalAmt\":,"
                + "\"stmtDate\":\"YYYY-MM-dd\","
                + "\"dueDate\":\"YYYY-MM-dd\","
                + "\"currentStatus\":\"Bill Generated\","
                + "\"month\":\"MM\","
                + "\"year\":\"YYYY\"}";

		UserMessage userMessage = new UserMessage(prompt, new Media(MimeType.valueOf("image/jpeg"), fileResource));
		log.info("Sending prompt to OpenAI!");
		return chatClient.prompt().messages(userMessage).call().chatResponse().getResult().getOutput().getText();
	}

}
