package com.abhinav.cc_backend_ai.controller;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Date;

import javax.imageio.ImageIO;

import org.apache.tomcat.util.codec.binary.Base64;
import org.springframework.ai.image.ImageOptions;
import org.springframework.ai.image.ImageOptionsBuilder;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.ai.openai.OpenAiImageModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import com.abhinav.cc_backend_ai.model.Answer;
import com.abhinav.cc_backend_ai.model.Mail;
import com.abhinav.cc_backend_ai.model.Question;
import com.abhinav.cc_backend_ai.service.MailService;
import com.abhinav.cc_backend_ai.service.OpenAIService;

import lombok.extern.slf4j.Slf4j;

@RestController
@CrossOrigin
@Slf4j
public class FrontController {
	
	@Autowired
	private OpenAIService openAIService;
	
	@Autowired
	MailService mailService;
	
	@Autowired
	OpenAiImageModel openAiImageModel;
	
	@Autowired
    private RestTemplate restTemplate;
	
	@Value("${authHeaderBP}")
	private String authHeaderBP;
	
	@Value("${ccBackendURL}")
	private String ccBackendURL;
	
	@PostMapping(path = "/ask", consumes = "application/json", produces = "application/json")
	public Answer getAnswer(@RequestBody Question question) {
		return openAIService.getAnswer(question);
	}
	
	@PostMapping(path = "/image", produces = MediaType.IMAGE_PNG_VALUE)
	public byte[] getImage(@RequestBody Question question) throws IOException {
		 ImageOptions options = ImageOptionsBuilder.builder()
	                .model("dall-e-3")
                    .N(1)
                    .height(1024)
                    .width(1024).responseFormat("b64_json")
	                .build();
		 	log.info("Input-->"+question.question());
	        ImagePrompt imagePrompt = new ImagePrompt(question.question(), options);
	        ImageResponse response = openAiImageModel.call(imagePrompt);

	        byte[] decodedBytes = Base64.decodeBase64(response.getResult().getOutput().getB64Json());
            ByteArrayInputStream bais = new ByteArrayInputStream(decodedBytes);
            BufferedImage image = ImageIO.read(bais);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "png", baos);
            
            File outputFile = new File("output.png");
            ImageIO.write(image, "png", outputFile);
            log.info("Image successfully written to file: " + outputFile.getAbsolutePath());

            return baos.toByteArray();
	}
	
	@GetMapping(path = "/auto", produces = "application/json")
	public String createAutoRecord() throws IOException {
		String response = null;
		Mail mail = mailService.getImageFromGmail();
		if(mail!=null && mail.getFile()!=null) {
			response = openAIService.extractDataFromImage(mail.getSubject(),mail.getFile());
			response = response.replace("```", "");
			response = response.replace("json", "");
			log.info("Response received from OpenAI!");
			response = callCCBackend(response);
			log.info("Response received from CC-Backend!");
			return response;
		}else {
			log.info("Data not received from mail, hence not sending to OpenAI!");
		}
		return null;
	}
	
	public String callCCBackend(String requestBody) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.set("Authorization", authHeaderBP);
		HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);
		log.info("Calling CC-Backend..");
		return restTemplate.postForEntity(ccBackendURL, entity, String.class).getBody();
	}
	
	@GetMapping(path = "/health")
	public String health() throws IOException {
		log.info("Inside health - " + new Date());
		return "CC Backend AI is up and running!";
	}

	
//	@GetMapping("/getPrompts")
//	public void getPrompts() {
//		openAIService.getPromptsByDate();
//	}

}
