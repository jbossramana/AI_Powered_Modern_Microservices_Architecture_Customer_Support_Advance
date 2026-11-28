package com.example.support.controller;

import com.example.support.model.ChatRequest;
import com.example.support.model.ChatResponse;
import com.example.support.service.ChatService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
public class ChatController {
	private final ChatService chatService;

	public ChatController(ChatService chatService) {
		this.chatService = chatService;
	}

	@PostMapping
	public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request, Authentication authentication) {
		return ResponseEntity.ok(chatService.answer(request.message(), authentication.getName()));
	}
}
