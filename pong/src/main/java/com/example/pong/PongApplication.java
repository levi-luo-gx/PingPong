package com.example.pong;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.Objects;
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
	public Mono<String> pong(ServerHttpRequest request) {
		long currentTime = System.currentTimeMillis() / 1000; // get current time in seconds

		int clientPort = Objects.requireNonNull(request.getRemoteAddress(), "Remote address not available").getPort();

		// Check if the request is within the same second
		if (currentTime == lastRequestTime && requestCount.get() >= MAX_REQUESTS_PER_SECOND) {
			// record the request and return 429
			logResult("Request from client port: " + clientPort + " received but too m" +
					"any requests, returning 429.");
			return Mono.error(new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many requests"));
		}
		// Update the last request time and increment the request count
		lastRequestTime = currentTime;
		requestCount.incrementAndGet();

		logResult("Pong Respond 'World' to client port: " + clientPort + ".");
		// Simulate processing delay and decrement request count after processing
		return Mono.just("World")
				.delayElement(Duration.ofSeconds(1))
				.doFinally(signalType -> requestCount.decrementAndGet());
	}

	private void logResult(String message) {
		String timestamp = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new java.util.Date());
		System.out.printf("[%s] %s%n",  timestamp, message);
	}
}
