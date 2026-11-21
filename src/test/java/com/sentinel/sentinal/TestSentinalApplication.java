package com.sentinel.sentinal;

import org.springframework.boot.SpringApplication;

public class TestSentinalApplication {

	public static void main(String[] args) {
		SpringApplication.from(SentinalApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
