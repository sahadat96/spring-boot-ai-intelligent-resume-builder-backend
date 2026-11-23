package com.opticareer.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController 
@SpringBootApplication
@ConfigurationPropertiesScan
public class ResumeBuilderApplication {

	public static void main(String[] args) {
		SpringApplication.run(ResumeBuilderApplication.class, args);
	}

	@GetMapping("/hello")
	public String hello() {
		return "Server is running";
	}

}
