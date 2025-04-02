package com.abhinav.cc_backend_ai.controller;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

import org.apache.tomcat.util.codec.binary.Base64;
import org.springframework.ai.image.ImageOptions;
import org.springframework.ai.image.ImageOptionsBuilder;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.ai.openai.OpenAiImageModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.abhinav.cc_backend_ai.model.Answer;
import com.abhinav.cc_backend_ai.model.Question;
import com.abhinav.cc_backend_ai.service.OpenAIService;

import lombok.extern.slf4j.Slf4j;

@RestController
@CrossOrigin
@Slf4j
public class FrontController {
	
	@Autowired
	private OpenAIService openAIService;
	
	@Autowired
	OpenAiImageModel openAiImageModel;
	
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

	
//	@GetMapping("/getPrompts")
//	public void getPrompts() {
//		openAIService.getPromptsByDate();
//	}

}
