package com.junbetterway.serverless.springcloudfunc;

import java.util.function.Function;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.Message;

import lombok.extern.log4j.Log4j2;

@SpringBootApplication
@Log4j2
public class SpringcloudfuncApplication {

	public static void main(String[] args) {
		SpringApplication.run(SpringcloudfuncApplication.class, args); 
	}

	@Bean
	public Function<Message<String>, String> greeter() {
		return (input) -> {
			log.info("Hello there {}", input.getPayload());
			return "Hello there " + input.getPayload();
		};
	}

}
