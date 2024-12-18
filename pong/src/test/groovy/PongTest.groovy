import com.example.pong.PongController
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import spock.lang.Specification
import reactor.test.StepVerifier
import reactor.core.publisher.Mono
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

@SpringBootTest
class PongTest extends Specification {


    def "test pong method returns World"() {
        given: "a Pong instance"
        def pong = new PongController()

        when: "the pong method is called"
        Mono<String> result = pong.pong()

        then: "the result should be 'World'"
        StepVerifier.create(result)
            .expectNext("World")
            .verifyComplete()
    }

    def "test pong method returns 429 when too many requests"() {
        given: "a Pong instance"
        def pong = new PongController()

        when: "the pong method is called too frequently"
        pong.pong().block() // First call
        Mono<String> result = pong.pong() // Second call within the same second

        then: "the result should be an error with status 429"
        StepVerifier.create(result)
            .expectErrorMatches { throwable ->
                throwable instanceof ResponseStatusException &&
                throwable.status == HttpStatus.TOO_MANY_REQUESTS
            }
            .verify()
    }

    def "test pong method logs correctly"() {
        given: "pong object is initialized"
        def pong = new PongController()
        assert pong != null : "pong object is not initialized"

        when: "the pong method is called"
        Mono<String> result = pong.pong()

        then: "logResult is called with 'Pong Respond.'"
        StepVerifier.create(result)
            .expectNext("World")
            .expectComplete()
            .verify()

        1 * pong.logResult("Pong Respond.")
    }
}
