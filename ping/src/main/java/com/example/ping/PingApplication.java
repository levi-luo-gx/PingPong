package com.example.ping;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.File;
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

	private WebClient webClient;

	public PingApplication() {
		this.webClient = WebClient.create();
	}

	public static void main(String[] args) {
		SpringApplication.run(PingApplication.class, args);
	}

	@PostConstruct
	private void init() {
		startPinging();
	}
	private void startPinging() {
			Flux.interval(Duration.ofMillis(1000))
					.flatMap(tick -> sendPing(webClient).subscribeOn(Schedulers.boundedElastic()))
					.subscribe(result -> logResult("Result: " + result));

	}
	private Mono<String> sendPing(WebClient client) {
			logResult("Attempting to send request...");
			if (tryLockFile(LOCK_FILE)) {
				return sendRequest(client);
			} else if (tryLockFile(LOCK_FILE_2)) {
				return sendRequest(client);
			} else {
				logResult("Request not sent as being 'rate limited");
				return Mono.just("Rate Limited");
			}
	}

	protected boolean tryLockFile(String lockFilePath) {
		try (FileChannel channel = createLock(lockFilePath)) {
			if (channel != null) {
				try (FileLock lock = channel.tryLock()) {
					if (lock != null) {
						Thread.sleep(1000); // 保持锁定一段时间
						return true;
					}
				}
			}
		} catch (IOException | InterruptedException e) {
			logResult("Error: " + e.getMessage());
		}
		return false;
	}

	protected FileChannel createLock(String lockFilePath) throws IOException {
		File file = new File(lockFilePath);
		if (file.exists()) {
			long lastModified = file.lastModified();
//			logResult("file exists: " + lockFilePath + ", last modified: " + new java.text.SimpleDateFormat("yyyy-MM-dd hh:mm:ss.SSS").format(new java.util.Date(lastModified)));
			long currentTime = System.currentTimeMillis();
			if (currentTime - lastModified > 5000) {
					Files.deleteIfExists(file.toPath());
					logResult("removing old lock file: " + lockFilePath + ", last modified: " + new java.text.SimpleDateFormat("yyyy-MM-dd hh:mm:ss.SSS").format(new java.util.Date(lastModified)));
			}
		}
		java.nio.file.Path path = Paths.get(lockFilePath);
		Files.createDirectories(path.getParent());
		FileChannel channel = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
		file.setLastModified(System.currentTimeMillis());
		return channel;
	}
	
	private Mono<String> sendRequest(WebClient client) {
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
				.onErrorResume(e -> Mono.just("Request sent & Pong throttled it."));
	}
	private void logResult(String message) {
		String timestamp = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new java.util.Date());
		System.out.printf("[%s] %s%n",  timestamp, message);
	}
}