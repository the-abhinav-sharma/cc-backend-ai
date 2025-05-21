package com.abhinav.cc_backend_ai.model;

import java.io.File;

import lombok.Data;

@Data
public class Mail {
	private String subject;
	private File file;
}
