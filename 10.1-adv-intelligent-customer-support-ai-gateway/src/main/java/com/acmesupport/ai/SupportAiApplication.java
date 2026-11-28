package com.acmesupport.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SupportAiApplication {
	public static void main(String[] args) {
		SpringApplication.run(SupportAiApplication.class, args);
	}
}
