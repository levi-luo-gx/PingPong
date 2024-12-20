import com.example.pong.PongController
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Mono
import spock.lang.Specification
import spock.lang.Subject
import java.time.Duration
import com.example.pong.PongController

class PongTest extends Specification {

    // 创建PongController实例作为测试的主体对象
//    @Subject
//    PongController pongController = new PongController()

    def "test pong method when requests within limit"() {
        def pongController = new PongController()
        given:
        // 模拟第一次请求，设置初始的lastRequestTime和requestCount
        pongController.lastRequestTime = System.currentTimeMillis() / 1000
        pongController.requestCount.set(0)

        when:
        Mono<String> result = pongController.pong()

        then:
        // 验证请求计数增加了1
        pongController.requestCount.get() == 1

        result.block() == "World"
    }

    def "test pong method when requests exceed limit in the same second"() {
        def pongController = new PongController()
        given:
        // 模拟在同一秒内超过请求限制的情况
        pongController.lastRequestTime = System.currentTimeMillis() / 1000
        pongController.requestCount.set(PongController.MAX_REQUESTS_PER_SECOND)

        when:
        Mono<String> response = pongController.pong()

        then:
        try {
            response.block()
        } catch (ResponseStatusException ex) {
            // 处理异常，获取状态码
            HttpStatus status = ex.getStatusCode();
            System.out.println("Caught exception with status: " + status);
            assert status == HttpStatus.TOO_MANY_REQUESTS
        }

    }

    def "test pong method with processing delay and request count decrement"() {
        def pongController = new PongController()

        given:
        pongController.lastRequestTime = System.currentTimeMillis() / 1000
        pongController.requestCount.set(0)

        when:
        Mono<String> result = pongController.pong()

        then:
        result.block(Duration.ofSeconds(2)) == "World"
        pongController.requestCount.get() == 0

    }

    def "test pong method when requests exceed limit across seconds"() {
        def pongController = new PongController()
        given:
        // 模拟跨秒请求
        pongController.lastRequestTime = System.currentTimeMillis() / 1000 - 1
        pongController.requestCount.set(PongController.MAX_REQUESTS_PER_SECOND)

        when:
        Mono<String> result = pongController.pong()

        then:
        result.block() == "World"
        pongController.requestCount.get() == 1
    }
}