package com.example.pong;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

@SpringBootApplication
public class PongApplication {

	public static void main(String[] args) {
		SpringApplication.run(PongApplication.class, args);
	}
}

@RestController
class PongController {

	// AtomicInteger to keep track of the number of requests
	private final AtomicInteger requestCount = new AtomicInteger(0);
	// Variable to track the last request time
	private long lastRequestTime = 0;
	// Variable to track the maximum requests per second
	private static final int MAX_REQUESTS_PER_SECOND = 1;

	@GetMapping("/pong")
	public Mono<String> pong() {
		long currentTime = System.currentTimeMillis() / 1000; // 获取当前秒数
		// Check if the request is within the same second
		if (currentTime == lastRequestTime && requestCount.get() >= MAX_REQUESTS_PER_SECOND) {
			// 记录429状态码的日志
			logResult("Request received but too many requests, returning 429.");
			return Mono.error(new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many requests"));
		}
		// Update the last request time and increment the request count
		lastRequestTime = currentTime;
		requestCount.incrementAndGet();

		logResult("Pong Respond.");
		// Simulate processing delay and decrement request count after processing
		return Mono.just("World")
				.delayElement(Duration.ofSeconds(1))
				.doFinally(signalType -> requestCount.decrementAndGet());
	}

	private void logResult(String message) {
		// Implement logging logic here
		System.out.println(message);
	}
}
