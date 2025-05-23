package com.abhinav.cc_backend_ai;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.abhinav.cc_backend_ai.controller.FrontController;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class ScheduledTasks {

	@Autowired
	FrontController controller;

	@Scheduled(cron = "0 00 11,23 * * *", zone = "Asia/Kolkata")
	public void execute() {
		try {
			controller.createAutoRecord();
		} catch (IOException e) {
			log.info("Failed to execute createAutoRecord - "+e.getMessage());
		}
	}
}