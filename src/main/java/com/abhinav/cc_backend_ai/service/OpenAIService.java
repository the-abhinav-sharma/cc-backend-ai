package com.abhinav.cc_backend_ai.service;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;

import com.abhinav.cc_backend_ai.model.AIMaster;
import com.abhinav.cc_backend_ai.model.Answer;
import com.abhinav.cc_backend_ai.model.Question;
import com.abhinav.cc_backend_ai.repository.AIMasterRepository;

import lombok.extern.slf4j.Slf4j;


@Service
@Slf4j
public class OpenAIService {

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
		log.info("For prompt: " + question.question() + ", response took: "
				+ timeTaken
				+ " milliseconds");

		String respText = response.getResult().getOutput().getText();
		
		if(respText.length()>1000) {
			respText = respText.substring(0, 1000);
		}
		
		aiMasterRepository.save(AIMaster.builder().prompt(question.question())
				.answer(respText).timeIn(String.valueOf(startTime))
				.timeOut(String.valueOf(stopTime))
				.respTime(timeTaken)
				.build());

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
		
		if(sb.length()==0) {
			sb.append("No prompts for today");
		}
			
		return sb.toString();
	}

}


