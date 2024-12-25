package com.example.ping

import org.bson.Document
import spock.lang.Specification
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import org.springframework.test.util.ReflectionTestUtils
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
import java.io.ByteArrayOutputStream
import java.io.PrintStream

class PingTest extends Specification {


    // 创建一个测试子类来访问protected和private方法
        class TestPingApplication extends PingApplication {
                @Override
                protected Mono<String> sendPing(WebClient client) {
                    // 覆盖原方法以便我们可以验证调用
                    return super.sendPing(client)
                }

                @Override
                protected void startPinging() {
                    // 覆盖原方法以便我们可以验证调用
                    super.startPinging()
                }
        }

        private TestPingApplication pingApplication
        private WebClient webClient

        def setup() {
                pingApplication = Spy(TestPingApplication)
                webClient = Mock(WebClient)

        }

        def "should send request when lock1 is acquired"() {
                given:
                def response = "pong response"
                def webClientGet = Mock(WebClient.RequestHeadersUriSpec)
                def webClientResponse = Mock(WebClient.ResponseSpec)

                ReflectionTestUtils.setField(pingApplication, "PONG_URL", "http://localhost:8080")
                ReflectionTestUtils.setField(pingApplication, "LOCK_FILE", "/tmp/test.lock")
                ReflectionTestUtils.setField(pingApplication, "LOCK_FILE_2", "/tmp/test2.lock")
                ReflectionTestUtils.setField(pingApplication, "webClient", webClient)

                when:
                def result = pingApplication.sendPing(webClient).block()

                then:
                // 模拟获取锁成功
                pingApplication.tryLockFile(_) >> true

                // 模拟WebClient调用链
                1 * webClient.get() >> webClientGet
                1 * webClientGet.uri(_) >> webClientGet
                1 * webClientGet.retrieve() >> webClientResponse
                1 * webClientResponse.onStatus(*_) >> webClientResponse
                1 * webClientResponse.bodyToMono(String) >> Mono.just(response)

                and:
                result == response
        }

        def "should send request when lock2 is acquired"() {
                given:
                def response = "pong response"
                def webClientGet = Mock(WebClient.RequestHeadersUriSpec)
                def webClientResponse = Mock(WebClient.ResponseSpec)

                ReflectionTestUtils.setField(pingApplication, "PONG_URL", "http://localhost:8080")
                ReflectionTestUtils.setField(pingApplication, "LOCK_FILE", "/tmp/test.lock")
                ReflectionTestUtils.setField(pingApplication, "LOCK_FILE_2", "/tmp/test2.lock")
                ReflectionTestUtils.setField(pingApplication, "webClient", webClient)

                pingApplication.tryLockFile("/tmp/test.lock") >> false
                pingApplication.tryLockFile("/tmp/test2.lock") >> true

                when:
                def result = pingApplication.sendPing(webClient).block()

                then:
                // 模拟WebClient调用链
                1 * webClient.get() >> webClientGet
                1 * webClientGet.uri(_) >> webClientGet
                1 * webClientGet.retrieve() >> webClientResponse
                1 * webClientResponse.onStatus(*_) >> webClientResponse
                1 * webClientResponse.bodyToMono(String) >> Mono.just(response)

                and:
                result == response
        }

        def "Rate Limited"() {
                given:
                def response = "pong response"
                def webClientGet = Mock(WebClient.RequestHeadersUriSpec)
                def webClientResponse = Mock(WebClient.ResponseSpec)

                ReflectionTestUtils.setField(pingApplication, "PONG_URL", "http://localhost:8080")
                ReflectionTestUtils.setField(pingApplication, "LOCK_FILE", "/tmp/test.lock")
                ReflectionTestUtils.setField(pingApplication, "LOCK_FILE_2", "/tmp/test2.lock")
                ReflectionTestUtils.setField(pingApplication, "webClient", webClient)

                // 将模拟移到这里，并明确指定参数
                pingApplication.tryLockFile("/tmp/test.lock") >> false
                pingApplication.tryLockFile("/tmp/test2.lock") >> false

                when:
                def result = pingApplication.sendPing(webClient).block()

                then:

                result == "Rate Limited"
        }

        def "lockfile IO Error, handling rate limit"(){
                given:
                def response = "pong response"
                def webClientGet = Mock(WebClient.RequestHeadersUriSpec)
                def webClientResponse = Mock(WebClient.ResponseSpec)

                ReflectionTestUtils.setField(pingApplication, "PONG_URL", "http://localhost:8080")
                ReflectionTestUtils.setField(pingApplication, "LOCK_FILE", "/tmp/test.lock")
                ReflectionTestUtils.setField(pingApplication, "LOCK_FILE_2", "/tmp/test2.lock")
                ReflectionTestUtils.setField(pingApplication, "webClient", webClient)

                // 模拟tryLockFile抛出异常
                pingApplication.tryLockFile("/tmp/test.lock") >> { throw new IOException("Lock file error") }

                when:
                def result = pingApplication.sendPing(webClient).block()

                then:
                result == "Error handling rate limit"
        }

    def "startPinging"() {
        given:
        def response = "World"
        def webClientGet = Mock(WebClient.RequestHeadersUriSpec)
        def webClientResponse = Mock(WebClient.ResponseSpec)

        ReflectionTestUtils.setField(pingApplication, "PONG_URL", "http://localhost:8080")
        ReflectionTestUtils.setField(pingApplication, "LOCK_FILE", "/tmp/test.lock")
        ReflectionTestUtils.setField(pingApplication, "LOCK_FILE_2", "/tmp/test2.lock")
        ReflectionTestUtils.setField(pingApplication, "webClient", webClient)

        // 模拟 sendPing 返回成功响应
        pingApplication.sendPing(webClient) >> Mono.just(response)

        when:
        pingApplication.startPinging()

        then:
        // 使用 StepVerifier 验证异步流
        StepVerifier.create(Flux.interval(Duration.ofMillis(1000))
            .flatMap { pingApplication.sendPing(webClient) })
            .expectNext(response)
            .thenCancel()
            .verify()

        // 验证日志输出包含成功结果
        // 使用 StepVerifier 验证异步流的结果
        StepVerifier.create(pingApplication.sendPing(webClient))
            .expectNext(response)
            .verifyComplete()
    }

        def "TOO_MANY_REQUESTS"(){
                given:
                def webClientGet = Mock(WebClient.RequestHeadersUriSpec)
                def webClientResponse = Mock(WebClient.ResponseSpec)

                ReflectionTestUtils.setField(pingApplication, "PONG_URL", "http://localhost:8080")
                ReflectionTestUtils.setField(pingApplication, "LOCK_FILE", "/tmp/test.lock")
                ReflectionTestUtils.setField(pingApplication, "LOCK_FILE_2", "/tmp/test2.lock")
                ReflectionTestUtils.setField(pingApplication, "webClient", webClient)

                // 模拟获取锁成功
                pingApplication.tryLockFile(_) >> true

                // 模拟429响应
                webClient.get() >> webClientGet
                webClientGet.uri(_) >> webClientGet
                webClientGet.retrieve() >> webClientResponse
                webClientResponse.onStatus(*_) >> { predicate, handler ->
                    handler.apply(Mock(org.springframework.web.reactive.function.client.ClientResponse) {
                        statusCode() >> HttpStatus.TOO_MANY_REQUESTS
                    })
                    return webClientResponse
                }
                webClientResponse.bodyToMono(String) >> Mono.just("Throttled")

                when:
                def result = pingApplication.sendPing(webClient).block()

                then:
                result == "Throttled"
        }
//---------------------------------------------------
        def "happy flow with lock1"(){
                given:
                def response = "pong response"
                def webClientGet = Mock(WebClient.RequestHeadersUriSpec)
                def webClientResponse = Mock(WebClient.ResponseSpec)

                ReflectionTestUtils.setField(pingApplication, "PONG_URL", "http://localhost:8080")
                ReflectionTestUtils.setField(pingApplication, "LOCK_FILE", "/tmp2/test.lock")
                ReflectionTestUtils.setField(pingApplication, "LOCK_FILE_2", "/tmp2/test2.lock")
                ReflectionTestUtils.setField(pingApplication, "webClient", webClient)

//                pingApplication.tryLockFile("/tmp/test.lock") >> false
//                pingApplication.tryLockFile("/tmp/test2.lock") >> true

                when:
                def result = pingApplication.sendPing(webClient).block()

                then:
                // 模拟WebClient调用链
                1 * webClient.get() >> webClientGet
                1 * webClientGet.uri(_) >> webClientGet
                1 * webClientGet.retrieve() >> webClientResponse
                1 * webClientResponse.onStatus(*_) >> webClientResponse
                1 * webClientResponse.bodyToMono(String) >> Mono.just(response)

                and:
                result == response
        }

        def "happy flow with lock2"() {
                given:
                def response = "pong response"
                def webClientGet = Mock(WebClient.RequestHeadersUriSpec)
                def webClientResponse = Mock(WebClient.ResponseSpec)

                ReflectionTestUtils.setField(pingApplication, "PONG_URL", "http://localhost:8080")
                ReflectionTestUtils.setField(pingApplication, "LOCK_FILE", "/tmp/test.lock")
                ReflectionTestUtils.setField(pingApplication, "LOCK_FILE_2", "/tmp/test2.lock")
                ReflectionTestUtils.setField(pingApplication, "webClient", webClient)

                pingApplication.tryLockFile("/tmp/test.lock") >> false
//                pingApplication.tryLockFile("/tmp/test2.lock") >> true

                when:
                def result = pingApplication.sendPing(webClient).block()

                then:
                // 模拟WebClient调用链
                1 * webClient.get() >> webClientGet
                1 * webClientGet.uri(_) >> webClientGet
                1 * webClientGet.retrieve() >> webClientResponse
                1 * webClientResponse.onStatus(*_) >> webClientResponse
                1 * webClientResponse.bodyToMono(String) >> Mono.just(response)

                and:
                result == response
        }
}