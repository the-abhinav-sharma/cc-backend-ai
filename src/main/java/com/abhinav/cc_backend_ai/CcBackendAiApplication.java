package com.abhinav.cc_backend_ai;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CcBackendAiApplication {

	public static void main(String[] args) {
		TimeZone.setDefault(TimeZone.getTimeZone("GMT+05:30"));
		SpringApplication.run(CcBackendAiApplication.class, args);
	}

}