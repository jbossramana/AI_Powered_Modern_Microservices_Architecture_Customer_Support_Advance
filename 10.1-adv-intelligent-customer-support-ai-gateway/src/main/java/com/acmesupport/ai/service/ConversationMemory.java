package com.acmesupport.ai.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ConversationMemory {
	public record Message(String role, String content) {
	}

	private final ConcurrentHashMap<String, List<Message>> sessions = new ConcurrentHashMap<>();
	@Value("${support.memory.max-messages:20}")
	int maxMessages;

	public List<Message> history(String id) {
		List<Message> l = sessions.get(id);
		if (l == null)
			return List.of();
		synchronized (l) {
			return List.copyOf(l);
		}
	}

	public void add(String id, String role, String content) {
		List<Message> l = sessions.computeIfAbsent(id, k -> Collections.synchronizedList(new ArrayList<>()));
		synchronized (l) {
			l.add(new Message(role, content));
			while (l.size() > maxMessages)
				l.remove(0);
		}
	}

	public int sessionCount() {
		return sessions.size();
	}

	public void clear(String id) {
		sessions.remove(id);
	}
}
