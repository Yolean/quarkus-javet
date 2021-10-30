package se.yolean.javet.quarkus.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class QuarkusJavetResourceTest {

  @Test
  public void testV8Mode() {
    given()
      .when().get("/quarkus-javet/v8")
      .then()
      .statusCode(200)
      .body(is("Javet V8 mode invoked"));
  }

  @Test
  @Disabled // currently only including the V8 lib
  public void testNodeMode() {
    given()
      .when().get("/quarkus-javet/node")
      .then()
      .statusCode(200)
      .body(is("Javet Node mode invoked"));
  }

}
