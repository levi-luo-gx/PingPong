package com.example.ping

import spock.lang.Specification
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import com.mongodb.reactivestreams.client.MongoCollection
import org.bson.Document
import com.mongodb.client.result.InsertOneResult
import static org.mockito.ArgumentMatchers.any
import static org.mockito.Mockito.*

class PingTest extends Specification {


    def "test tryLock success"() {
        given:
        def lockCollection = Mock(MongoCollection)
        def pingApplication = new PingApplication()
        pingApplication.lockCollection = lockCollection

        when:
        def result = pingApplication.tryLock("testLock").block()

        then:
        1 * lockCollection.insertOne(_) >> Mono.just(Mock(InsertOneResult))
        result == true
    }

    def "test tryLock failure"() {
        given:
        def lockCollection = Mock(MongoCollection)
        def pingApplication = new PingApplication()
        pingApplication.lockCollection = lockCollection

        when:
        def result = pingApplication.tryLock("testLock").block()

        then:
        1 * lockCollection.insertOne(_) >> Mono.error(new Exception("Duplicate key"))
        result == false
    }

    def "test releaseLock success"() {
        given:
        def lockCollection = Mock(MongoCollection)
        def pingApplication = new PingApplication()
        pingApplication.lockCollection = lockCollection

        when:
        pingApplication.releaseLock("testLock").block()

        then:
        1 * lockCollection.deleteOne(_) >> Mono.empty()
    }

    def "test releaseLock failure"() {
        given:
        def lockCollection = Mock(MongoCollection)
        def pingApplication = new PingApplication()
        pingApplication.lockCollection = lockCollection

        when:
        pingApplication.releaseLock("testLock").block()

        then:
        1 * lockCollection.deleteOne(_) >> Mono.error(new Exception("Delete failed"))
        thrown(Exception)
    }

    def "test sendPing success with lock acquired and request successful"() {
        given: "a mocked WebClient and response"
        def responseSpec = Mock(WebClient.ResponseSpec)
        def requestHeadersUriSpec = Mock(WebClient.RequestHeadersUriSpec)
        def requestHeadersSpec = Mock(WebClient.RequestHeadersSpec)

        def webClient = Mock(WebClient) // Mock the WebClient

        // Mock the chain of WebClient calls
//        when(webClient.get()).thenReturn(requestHeadersUriSpec)
//        when(requestHeadersUriSpec.uri("/ping")).thenReturn(requestHeadersSpec)
//        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec)
//        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just("pong"))

        and: "a Ping instance with mocked MongoCollection"
        def ping = new PingApplication(webClient: webClient)
        ping.lockCollection = Mock(MongoCollection)
        when(ping.lockCollection.insertOne(any(Document.class))).thenReturn(Mono.empty()) // Ensure Mono is returned

        when: "the ping method is called"
        def result = ping.sendPing().block()

        then: "the result should be as expected"
        result == "pong"
    }


    def "test sendPing failure with lock acquisition failure"() {
        given:
        WebClient webClient = WebClient.create();
        def lockCollection = Mock(MongoCollection)
        def pingApplication = new PingApplication()
        pingApplication.lockCollection = lockCollection

        // Mock the behavior of lockCollection to simulate lock acquisition failure
        lockCollection.insertOne(_) >> Mono.error(new Exception("Lock acquisition failed"))

        when:
        def result = pingApplication.sendPing(webClient).block()

        then:
        0 * pingApplication.releaseLock("pingLock1")
        result == "Rate Limited"
    }

    def "test sendPing failure with request failure"() {
        given:
        def lockCollection = Mock(MongoCollection)
        def webClient = Mock(WebClient)
        def pingApplication = new PingApplication()
        pingApplication.lockCollection = lockCollection
        pingApplication.webClient = webClient

        // Mock WebClient to return an error response
        def webClientResponse = Mock(WebClient.ResponseSpec)
        webClient.get() >> webClient
        webClient.uri(_) >> webClient
        webClient.retrieve() >> webClientResponse
        webClientResponse.bodyToMono(String.class) >> Mono.error(new Exception("Request failed"))

        when:
        def result = pingApplication.sendPing().block()

        then:
        1 * pingApplication.tryLock("pingLock1") >> Mono.just(true)
        1 * pingApplication.releaseLock("pingLock1") >> Mono.empty()
        result == null
    }

    def "test sendPing when rate limited"() {
        given: "tryLock returns false for both locks"
        def pingApplication = new PingApplication()
        pingApplication.metaClass.tryLock = { String lockName -> Mono.just(false) }
        def webClient = Mock(WebClient)
        pingApplication.webClient = webClient

        when: "sendPing is called"
        def result = pingApplication.sendPing().block()

        then: "the result should be 'Rate Limited'"
        result == "Rate Limited"
    }

    def "test sendPing with rate limiting"() {
        given:
        def client = Mock(WebClient)
        def lockCollection = Mock(MongoCollection)
        def pingApp = new PingApplication()
        pingApp.lockCollection = lockCollection

        when:
        lockCollection.insertOne(_) >> Mono.error(new Exception("Lock failed"))
        def result = pingApp.sendPing(client).block()

        then:
        result == "Rate Limited"
    }
}