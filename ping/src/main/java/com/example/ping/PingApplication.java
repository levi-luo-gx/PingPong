package com.example.ping;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.springframework.beans.factory.annotation.Value;
import jakarta.annotation.PostConstruct;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.client.model.Filters;
import org.bson.Document;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@SpringBootApplication
public class PingApplication {

	@Value("${pong.url}")
	private String PONG_URL;

	@Value("${lock.file}")
	private String LOCK_FILE;

	@Value("${lock.file2}")
	private String LOCK_FILE_2;

	private MongoClient mongoClient;
	private MongoCollection<Document> lockCollection;

	public static void main(String[] args) {
		SpringApplication.run(PingApplication.class, args);
	}

	@PostConstruct
	private void init() {
		mongoClient = MongoClients.create("mongodb://localhost:27017");
		lockCollection = mongoClient.getDatabase("ping").getCollection("locks");
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

	private Mono<Boolean> tryLock(String lockName) {
		return Mono.from(lockCollection.insertOne(new Document("_id", lockName)))
				.map(result -> true)
				.onErrorResume(e -> Mono.just(false));
	}

	private Mono<Void> releaseLock(String lockName) {
		return Mono.from(lockCollection.deleteOne(Filters.eq("_id", lockName)))
				.then();
	}

	private Mono<String> sendPing(WebClient client) {
		return tryLock("pingLock1")
				.flatMap(locked1 -> {
					logResult("current time: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
					// logResult("pingLock1: " + locked1);
					if (locked1) {
						return Mono.just("pingLock1");
					} else {
						return tryLock("pingLock2")
								.flatMap(locked2 -> {
									// logResult("pingLock2: " + locked2);
									if (locked2) {
										return Mono.just("pingLock2");
									} else {
										logResult("Request not sent as being 'rate limited");
										return Mono.just("Rate Limited");
									}
								});
					}
				})
				.flatMap(lockedName -> {
					if (lockedName != null && !lockedName.equals("Rate Limited")) {
						return client.get()
								.uri(PONG_URL)
								.retrieve()
								.bodyToMono(String.class)
								.doOnNext(response -> logResult("Request sent & Pong Respond: " + response))
								.doFinally(signalType -> 
									Mono.delay(Duration.ofMillis(999))
										.then(releaseLock(lockedName))
										.subscribe()
								)
								.onErrorResume(e -> {
									logResult("Request sent & received 429 Too Many Requests.");
									return Mono.just("Throttled");
								});
					} else {
						return Mono.just("Rate Limited");
					}
				});
	}

	private void logResult(String message) {
		// Implement logging logic here
		System.out.println(message);
	}

}