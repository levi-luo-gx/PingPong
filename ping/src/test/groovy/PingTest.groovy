package com.example.ping

import org.springframework.web.reactive.function.client.ClientResponse
import reactor.core.publisher.Flux
import spock.lang.Specification
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.http.HttpStatus
import reactor.test.StepVerifier
import java.nio.channels.FileLock
import java.nio.file.StandardOpenOption;
import java.nio.channels.FileChannel
import java.nio.file.Paths;

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

                @Override
                protected void init() {
                    // 覆盖原方法以便我们可以验证调用
                    super.init()
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

        def "lockfile IO Error, handling rate limit"() {
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
                def result = ""
                try {
                    result = pingApplication.sendPing(webClient).block()
                } catch (IOException e) {
                    result = "Error handling rate limit"
                }

                then:
                result == "Error handling rate limit"
        }

        def "test init"() {
                given:
                def response = "pong response"
                def webClientGet = Mock(WebClient.RequestHeadersUriSpec)
                def webClientResponse = Mock(WebClient.ResponseSpec)

                ReflectionTestUtils.setField(pingApplication, "PONG_URL", "http://localhost:8080")
                ReflectionTestUtils.setField(pingApplication, "LOCK_FILE", "/tmp/test.lock")
                ReflectionTestUtils.setField(pingApplication, "LOCK_FILE_2", "/tmp/test2.lock")
                ReflectionTestUtils.setField(pingApplication, "webClient", webClient)

                // 模拟获取锁成功
                pingApplication.tryLockFile(_) >> true

                // 模拟正常响应
                webClient.get() >> webClientGet
                webClientGet.uri(_) >> webClientGet
                webClientGet.retrieve() >> webClientResponse
                webClientResponse.onStatus(*_) >> webClientResponse
                webClientResponse.bodyToMono(String) >> Mono.just(response)

                when:
                pingApplication.init()
                Thread.sleep(1500) // 等待第一次interval触发

                then:
                1 * webClient.get() // 验证至少调用了一次get请求
        }

        def "exception TOO_MANY_REQUESTS 429"(){
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
                    handler.apply(Mock(ClientResponse) {
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

    def "other exception from Pong"(){
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
            handler.apply(Mock(ClientResponse) {
                statusCode() >> HttpStatus.PRECONDITION_REQUIRED
            })
            return webClientResponse
        }
        webClientResponse.bodyToMono(String) >> Mono.just("Error handling rate limit")

        when:
        def result = pingApplication.sendPing(webClient).block()

        then:
        result == "Error handling rate limit"
    }

        def "happy flow with lock1"(){
                given:
                def response = "pong response"
                def webClientGet = Mock(WebClient.RequestHeadersUriSpec)
                def webClientResponse = Mock(WebClient.ResponseSpec)

                ReflectionTestUtils.setField(pingApplication, "PONG_URL", "http://localhost:8080")
                ReflectionTestUtils.setField(pingApplication, "LOCK_FILE", "/tmp2/test.lock")
                ReflectionTestUtils.setField(pingApplication, "LOCK_FILE_2", "/tmp2/test2.lock")
                ReflectionTestUtils.setField(pingApplication, "webClient", webClient)

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

   def "test tryLockFile return false"(){
        given:
        def lockFilePath = "/tmp/test.lock"
        
        // 模拟createLock返回null
        pingApplication.createLock(lockFilePath) >> null
        
        when:
        def result = pingApplication.tryLockFile(lockFilePath)
        
        then:
        result == false
   }

def "should handle IOException in tryLockFile"() {
    given:
    def lockFilePath = "/tmp/test.lock"
    pingApplication.createLock(lockFilePath) >> { throw new IOException("IO error") }

    when:
    def result = pingApplication.tryLockFile(lockFilePath)

    then:
    result == false
}

}