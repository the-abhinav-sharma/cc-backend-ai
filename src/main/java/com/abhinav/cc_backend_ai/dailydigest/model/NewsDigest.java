package com.abhinav.cc_backend_ai.dailydigest.model;

import java.util.List;

public record NewsDigest(String date, List<NewsStory> stories) {
}