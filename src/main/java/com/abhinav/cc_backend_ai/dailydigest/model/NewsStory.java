package com.abhinav.cc_backend_ai.dailydigest.model;

public record NewsStory(String category, String headline, String summary, String source, String publishedDate,
		String url, String imagePath) {
}