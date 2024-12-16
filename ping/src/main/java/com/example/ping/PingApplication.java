package com.example.ping;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.Files;
import java.time.Duration;
import java.util.List;

@SpringBootApplication
public class PingApplication {

	// URL of the Pong service
	private static final String PONG_URL = "http://localhost:8080/pong";
	// File used for rate limiting across processes
	private static final String LOCK_FILE = "C:\\Users\\levig\\Desktop\\Projects\\PingPong\\rate_limit.lock";

	public static void main(String[] args) {
		SpringApplication.run(PingApplication.class, args);
		new PingApplication().startPinging();
	}

	private void startPinging() {
		WebClient client = WebClient.create();
		try {

			Flux.interval(Duration.ofMillis(1000))
					.flatMap(tick -> sendPing(client).subscribeOn(Schedulers.boundedElastic()))
					.subscribe(result -> System.out.println("Result: " + result),
							error -> System.err.println("Error: " + error.getMessage()));
		} catch (Exception e) {
			logResult("Error reading/writing count: " + e.getMessage());
		}
	}

	private Mono<String> sendPing(WebClient client) {
		try {
			logResult("Attempting to send request...");
			int currentCount = readCountFromFile();

			System.out.println("Current Count: " + currentCount);
			if (currentCount < 2) {
				writeCountToFile(currentCount + 1);
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					logResult("error: " + e.getMessage());
				}
				return client.get()
						.uri(PONG_URL)
						.retrieve()
						.onStatus(HttpStatusCode::is4xxClientError, response -> {
							if (response.statusCode() == HttpStatus.TOO_MANY_REQUESTS) {
								logResult("Request sent & Pong responded with 429 Too Many Requests.");
								return Mono.error(new RuntimeException("Throttled"));
							}
							return Mono.error(new RuntimeException("Client error: " + response.statusCode()));
						})
						.bodyToMono(String.class)
						.doOnNext(response -> {
							logResult("Request sent & Pong Respond: " + response);
							decrementCount();
						})
						.onErrorResume(e -> {
							logResult("Request sent & Pong throttled it.");
							decrementCount();
							return Mono.just("Throttled");
						});
			} else {
				logResult("Rate limit exceeded, request not sent.");
				return Mono.just("Rate Limited");
			}
		} catch (Exception e) {
			logResult("Error handling rate limit: " + e.getMessage());
			e.printStackTrace();
			return Mono.just("Error handling rate limit");
		}
	}

	private void decrementCount() {
		try {
			int currentCount = readCountFromFile();
			if (currentCount > 0) {
				writeCountToFile(currentCount - 1);
			}
		} catch (IOException e) {
			logResult("Error decrementing count: " + e.getMessage());
		}
	}

	private int readCountFromFile() throws IOException {
		// 检查文件是否存在，如果不存在则创建
		if (!Files.exists(Paths.get(LOCK_FILE))) {
			Files.createFile(Paths.get(LOCK_FILE));
		}
		List<String> lines = Files.readAllLines(Paths.get(LOCK_FILE));
		return lines.isEmpty() ? 0 : Integer.parseInt(lines.get(0));
	}

	private void writeCountToFile(int count) throws IOException {
		Files.write(Paths.get(LOCK_FILE), String.valueOf(count).getBytes(), StandardOpenOption.WRITE);
	}

	private void logResult(String message) {
		// Implement logging logic here
		System.out.println(message);
	}

}