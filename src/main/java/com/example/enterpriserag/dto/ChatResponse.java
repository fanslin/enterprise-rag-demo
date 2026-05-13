package com.example.enterpriserag.dto;

import java.util.List;

public record ChatResponse(String answer, List<Citation> citations) {
}
