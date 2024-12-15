package com.example.ping;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.RandomAccessFile;
import java.nio.channels.FileLock;
import java.nio.file.Paths;
import java.time.Duration;

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
		// 每1000毫秒发出一个信号，直接发送请求
		Flux.interval(Duration.ofMillis(1000)) // 每1000毫秒发出一个信号
				.flatMap(tick -> sendPing(client).subscribeOn(Schedulers.boundedElastic()))
				.subscribe(result -> System.out.println("Result: " + result),
						error -> System.err.println("Error: " + error.getMessage()));
	}

	private Mono<String> sendPing(WebClient client) {
		try (RandomAccessFile file = new RandomAccessFile(Paths.get(LOCK_FILE).toFile(), "rw");
			 FileLock lock = file.getChannel().lock()) {

			logResult("Attempting to acquire lock...");
			if (lock != null) {
				logResult("Lock acquired.");
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
						.doOnNext(response -> logResult("Request sent & Pong Respond: " + response))
						.onErrorResume(e -> {
							logResult("Request sent & Pong throttled it.");
							return Mono.just("Throttled");
						});
			} else {
				System.out.println("Lock not acquired.");
				logResult("Request not sent as being rate limited.");
				return Mono.just("Rate Limited");
			}
		} catch (Exception e) {
			return Mono.error(e);
		}
	}

	private void logResult(String message) {
		// Implement logging logic here
		System.out.println(message);
	}

}