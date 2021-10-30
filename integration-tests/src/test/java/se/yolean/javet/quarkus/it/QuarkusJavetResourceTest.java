package se.yolean.javet.quarkus.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class QuarkusJavetResourceTest {

    @Test
    public void testHelloEndpoint() {
        given()
                .when().get("/quarkus-javet")
                .then()
                .statusCode(200)
                .body(is("Hello quarkus-javet"));
    }
}
