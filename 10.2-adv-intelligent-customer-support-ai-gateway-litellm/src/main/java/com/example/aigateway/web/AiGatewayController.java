
package com.example.aigateway.web;

import com.example.aigateway.dto.ChatRequest;
import com.example.aigateway.dto.ChatResponse;
import com.example.aigateway.service.AiGatewayService;

import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai")
public class AiGatewayController {

    private static final Logger log =
            LoggerFactory.getLogger(AiGatewayController.class);

    private final AiGatewayService service;

    public AiGatewayController(AiGatewayService service) {
        this.service = service;
    }

    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(
            @Valid @RequestBody ChatRequest request) {

        log.info("Received AI request. Configured model=llama3.2");

        ChatResponse response = service.chat(request);

        log.info("Returning AI response. RequestId={}",
                response.requestId());

        return ResponseEntity.ok(response);
    }
}
