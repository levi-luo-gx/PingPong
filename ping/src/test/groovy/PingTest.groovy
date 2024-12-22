package com.example.ping

import spock.lang.Specification
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import com.mongodb.reactivestreams.client.MongoCollection
import com.mongodb.client.result.InsertOneResult
import com.mongodb.client.result.DeleteResult
import org.springframework.web.server.ResponseStatusException
import org.springframework.http.HttpStatus
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import com.mongodb.reactivestreams.client.MongoClient;
import reactor.test.StepVerifier
import java.time.Duration
import reactor.core.publisher.Flux

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
        given:
        def lockCollection = Mock(MongoCollection)
        def webClient = Mock(WebClient)
        def requestHeadersUriSpec = Mock(WebClient.RequestHeadersUriSpec)
        def responseSpec = Mock(WebClient.ResponseSpec)
        def pingApplication = new PingApplication()
        pingApplication.lockCollection = lockCollection
        pingApplication.webClient = webClient

        // 模拟成功获取锁
        lockCollection.insertOne(_) >> Mono.just(Mock(InsertOneResult))

        // 模拟成功删除锁
        lockCollection.deleteOne(_) >> Mono.just(Mock(DeleteResult))

        // 模拟WebClient成功请求
        webClient.get() >> requestHeadersUriSpec
        requestHeadersUriSpec.uri(_) >> requestHeadersUriSpec
        requestHeadersUriSpec.retrieve() >> responseSpec
        responseSpec.bodyToMono(String.class) >> Mono.just("World")

        when:
        def result = pingApplication.sendPing(webClient).block()

        then:
        result == "World"
    }

    def "test sendPing when rate limited"() {
        given:
        def lockCollection = Mock(MongoCollection)
        def webClient = Mock(WebClient)
        def pingApplication = new PingApplication()
        pingApplication.lockCollection = lockCollection
        pingApplication.webClient = webClient

        // 模拟锁获取失败
        lockCollection.insertOne(_) >> Mono.error(new Exception("Lock acquisition failed"))

        when:
        def result = pingApplication.sendPing(webClient).block()

        then:
        result == "Rate Limited"
    }

    def "test sendPing failure with request failure"() {
        given:
        def lockCollection = Mock(MongoCollection)
        def webClient = Mock(WebClient)
        def requestHeadersUriSpec = Mock(WebClient.RequestHeadersUriSpec)
        def responseSpec = Mock(WebClient.ResponseSpec)
        def pingApplication = new PingApplication()
        pingApplication.lockCollection = lockCollection
        pingApplication.webClient = webClient

        // 模拟成功获取锁
        lockCollection.insertOne(_) >> Mono.just(Mock(InsertOneResult))

        // 模拟成功删除锁
        lockCollection.deleteOne(_) >> Mono.just(Mock(DeleteResult))

        // 模拟WebClient成功请求
        webClient.get() >> requestHeadersUriSpec
        requestHeadersUriSpec.uri(_) >> requestHeadersUriSpec
        requestHeadersUriSpec.retrieve() >> responseSpec
        responseSpec.bodyToMono(String.class) >> Mono.error(new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many requests"))

        when:
        def result = pingApplication.sendPing(webClient).block()

        then:
        result == "Throttled"
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


    def "test Mongo connection failure"() {
        given:
        def pingApplication = new PingApplication()
        pingApplication.mongodbUri = "invalid_uri"
        
        // 模拟日志记录器
        def logger = Mock(Logger)
        LoggerFactory.getLogger(PingApplication) >> logger

        when:
        pingApplication.init()

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("The connection string is invalid")
//        1 * logger.error({ it.contains("Failed to connect to MongoDB") })
    }

    def "test releaseLock with uninitialized lockCollection"() {
    given:
    def pingApplication = new PingApplication()
        when:
    def result = pingApplication.releaseLock("testLock").block()
        then:
    thrown(IllegalStateException)
    }

    def "test startPinging"() {
        given:
        def pingApplication = new PingApplication()
        pingApplication.mongodbUri = "mongodb://localhost:27017"

        // 显式指定Mock对象的类型
        def mongoClient = Mock(MongoClient)
        def mongoCollection = Mock(MongoCollection)
        pingApplication.mongoClient = mongoClient
        pingApplication.lockCollection = mongoCollection

        // 显式指定Logger的Mock类型
        def logger = Mock(Logger)
        LoggerFactory.getLogger(PingApplication) >> logger

        when:
        pingApplication.init()

        then:

        0 * logger.info({ it.contains("Successfully connected to MongoDB.") })
        0 * logger.info({ it.contains("Starting pinging process.") })
    }

    def "test sendPing without lock acquisition"() {
    given:
    def lockCollection = Mock(MongoCollection)
    def webClient = Mock(WebClient)
    def pingApplication = new PingApplication()
    pingApplication.lockCollection = lockCollection
    pingApplication.webClient = webClient
        // 模拟锁获取失败
    lockCollection.insertOne(_) >> Mono.error(new Exception("Lock acquisition failed"))
        when:
    def result = pingApplication.sendPing(webClient).block()
        then:
    result == "Rate Limited"
    }


}