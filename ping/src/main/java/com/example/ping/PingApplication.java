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
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.Files;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import jakarta.annotation.PostConstruct;

@SpringBootApplication
public class PingApplication {

	@Value("${pong.url}")
	private String PONG_URL;

	@Value("${lock.file}")
	private String LOCK_FILE;

	@Value("${lock.file2}")
	private String LOCK_FILE_2;

	public static void main(String[] args) {
		SpringApplication.run(PingApplication.class, args);
	}

	@PostConstruct
	private void init() {
		startPinging();
	}

	private void startPinging() {
		WebClient client = WebClient.create();
		try {

			Flux.interval(Duration.ofMillis(1000))
					.flatMap(tick -> sendPing(client).subscribeOn(Schedulers.boundedElastic()))
					.subscribe(result -> logResult("Result: " + result),
							error -> logResult("Error: " + error.getMessage()));
		} catch (Exception e) {
			logResult("Error reading/writing count: " + e.getMessage());
		}
	}

	private Mono<String> sendPing(WebClient client) {
		try {
			logResult("Attempting to send request...");
			if (tryLockFile(LOCK_FILE) || tryLockFile(LOCK_FILE_2)) {
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
						})
						.onErrorResume(e -> {
							logResult("Request sent & Pong throttled it.");
							if (!(e instanceof RuntimeException && e.getMessage().equals("Throttled"))) {
								e.printStackTrace();
							}
							return Mono.just("Throttled");
						});
			} else {
				logResult("Request not sent as being 'rate limited");
				return Mono.just("Rate Limited");
			}
		} catch (Exception e) {
			e.printStackTrace();
			return Mono.just("Error handling rate limit");
		}
	}

	private boolean tryLockFile(String lockFilePath) {
		try {
			java.nio.file.Path path = Paths.get(lockFilePath);
			Files.createDirectories(path.getParent());
			
			FileChannel channel = FileChannel.open(path, 
				StandardOpenOption.CREATE, 
				StandardOpenOption.WRITE);
			FileLock lock = channel.tryLock();
			if (lock != null) {
				Thread.sleep(1000); // Hold the lock file for 1 second
				lock.release();
				channel.close();
				return true;
			}
			channel.close();
		} catch (IOException | InterruptedException e) {
			logResult("Error locking file: " + e.getMessage());
		}
		return false;
	}

	private void logResult(String message) {
		// Implement logging logic here
		System.out.println(message);
	}

}