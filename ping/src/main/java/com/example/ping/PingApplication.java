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

	private static class LockHolder {
		FileLock lock;
		FileChannel channel;
	}

	public static void main(String[] args) {
		SpringApplication.run(PingApplication.class, args);
	}

	@PostConstruct
	private void init() {
		startPinging();
	}

	private void startPinging() {
		try {
			Flux.interval(Duration.ofMillis(1000))
					.flatMap(tick -> sendPing(webClient).subscribeOn(Schedulers.boundedElastic()))
					.subscribe(result -> logResult("Result: " + result),
							error -> logResult("Error: " + error.getMessage()));
		} catch (Exception e) {
			logResult("Error reading/writing count: " + e.getMessage());
		}
	}
	protected Mono<String> sendPing(){
		System.out.println("sendPing");
		return null;
	}
	private Mono<String> sendPing(WebClient client) {
		try {
			logResult("Attempting to send request...");
			if (tryLockFile(LOCK_FILE)) {
				return sendRequest(client);
			} else if (tryLockFile(LOCK_FILE_2)) {
				return sendRequest(client);
			} else {
				logResult("Request not sent as being 'rate limited");
				return Mono.just("Rate Limited");
			}
		} catch (Exception e) {
			return Mono.just("Error handling rate limit");
		}
	}

	protected boolean tryLockFile(String lockFilePath) {
		final LockHolder holder = new LockHolder();
		try {
			holder.channel = createLock(lockFilePath);
			if (holder.channel != null) {
				holder.lock = holder.channel.tryLock();
				if (holder.lock != null) {
					return true;
				}
			}
		} catch (IOException e) {
			logResult("Error locking file: " + e.getMessage());
		} finally {
			if (holder.lock != null) {
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					logResult("Sleep interrupted: " + e.getMessage());
				}
				releaseLock(holder.lock, holder.channel);
			}
		}
		return false;
	}



	private FileChannel createLock(String lockFilePath) throws IOException {
		File file = new File(lockFilePath);
		if (file.exists()) {
			long lastModified = file.lastModified();
//			logResult("file exists: " + lockFilePath + ", last modified: " + new java.text.SimpleDateFormat("yyyy-MM-dd hh:mm:ss.SSS").format(new java.util.Date(lastModified)));
			long currentTime = System.currentTimeMillis();
			if (currentTime - lastModified > 5000) {
				try {
					Files.deleteIfExists(file.toPath());
					logResult("removing old lock file: " + lockFilePath + ", last modified: " + new java.text.SimpleDateFormat("yyyy-MM-dd hh:mm:ss.SSS").format(new java.util.Date(lastModified)));
				} catch (IOException e) {
					logResult("remove old lock file failed: " + e.getMessage());
				}
			}
		}
		java.nio.file.Path path = Paths.get(lockFilePath);
		Files.createDirectories(path.getParent());
		FileChannel channel = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
		file.setLastModified(System.currentTimeMillis());
		return channel;
	}

	private void releaseLock(FileLock lock, FileChannel channel) {
		try {
			if (lock != null) {
				lock.release();
			}
			if (channel != null) {
				channel.close();
			}
		} catch (IOException e) {
			e.printStackTrace();
			logResult("Error releasing lock: " + e.getMessage());
		}
	}

	private void logResult(String message) {
		// Implement logging logic here
		System.out.println(message);
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
				.onErrorResume(e -> {
					logResult("Request sent & Pong throttled it.");
					if (!(e instanceof RuntimeException && e.getMessage().equals("Throttled"))) {
						e.printStackTrace();
					}
					return Mono.just("Throttled");
				});
	}

}