package com.example.ping;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import jakarta.annotation.PostConstruct;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.client.model.Filters;
import org.bson.Document;

@SpringBootApplication
public class PingApplication {

	@Value("${pong.url}")
	private String PONG_URL;

	@Value("${spring.data.mongodb.uri}")
    private String mongodbUri;
	private MongoClient mongoClient;
	private MongoCollection<Document> lockCollection;

	private WebClient webClient;
	public PingApplication() {
		this.webClient = WebClient.create();
	}

	public static void main(String[] args) {
		SpringApplication.run(PingApplication.class, args);
	}

	@PostConstruct
	private void init() {
		logResult("Connecting to MongoDB at: " + mongodbUri);
		try {
			mongoClient = MongoClients.create(mongodbUri);
			lockCollection = mongoClient.getDatabase("ping").getCollection("locks");
			logResult("Successfully connected to MongoDB.");
		} catch (Exception e) {
			logResult("Failed to connect to MongoDB: " + e.getMessage());
			throw new IllegalArgumentException("The connection string is invalid", e);
		}
		startPinging();
	}

	private void startPinging() {
//		WebClient client = WebClient.create();
		try {
			logResult("Starting pinging process.");
			Flux.interval(Duration.ofMillis(1000))
					.flatMap(tick -> sendPing(webClient).subscribeOn(Schedulers.boundedElastic()))
					.subscribe(result -> logResult("Result: " + result),
							error -> logResult("Error: " + error.getMessage()));
		} catch (Exception e) {
			logResult("Error reading/writing count: " + e.getMessage());
		}
	}

	private Mono<Boolean> tryLock(String lockName) {
		return Mono.from(lockCollection.insertOne(new Document("_id", lockName)))
				.map(result -> true)
				.onErrorResume(e -> Mono.just(false));
	}

	private Mono<Void> releaseLock(String lockName) {
		if (lockCollection == null) {
			return Mono.error(new IllegalStateException("Lock collection is not initialized"));
		}
		return Mono.delay(Duration.ofSeconds(1))
				.then(Mono.from(lockCollection.deleteOne(Filters.eq("_id", lockName))))
				.then();
	}

	private Mono<String> sendPing(WebClient client) {
		logResult("Attempting to send ping.");
		return tryAcquireLock("pingLock1")
				.switchIfEmpty(tryAcquireLock("pingLock2"))
				.defaultIfEmpty("Rate Limited")
				.flatMap(lockedName -> {
					if (!lockedName.equals("Rate Limited")) {
						return client.get()
								.uri(PONG_URL)
								.retrieve()
								.bodyToMono(String.class)
								.doOnNext(response -> logResult(String.format("Request sent & Pong Respond: %s", response)))
								.onErrorResume(e -> {
									logResult("Request sent & received 429 Too Many Requests.");
									return Mono.just("Throttled");
								});
					} else {
						logResult("Rate Limited, no lock acquired.");
						return Mono.just("Rate Limited");
					}
				});
	}

	private Mono<String> tryAcquireLock(String lockName) {
		return tryLock(lockName)
				.flatMap(locked -> {
					if (locked) {
						logResult("Acquired lock: " + lockName);
						releaseLock(lockName)
							.doOnSuccess(result -> logResult(lockName + " released."))
							.subscribe();
						return Mono.just(lockName);
					} else {
						logResult("Failed to acquire lock: " + lockName);
						return Mono.empty();
					}
				});
	}

	private void logResult(String message) {
		String timestamp = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new java.util.Date());
		System.out.println(String.format("[%s] %s",  timestamp, message));
	}

}