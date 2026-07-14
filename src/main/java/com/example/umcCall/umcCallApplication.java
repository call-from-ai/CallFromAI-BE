package com.example.umcCall;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class umcCallApplication {

	public static void main(String[] args) {
		SpringApplication.run(umcCallApplication.class, args);
	}

}
