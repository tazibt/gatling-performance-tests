package simulations;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

import io.gatling.javaapi.core.*;
import io.gatling.javaapi.http.*;
import java.time.Duration;

public class EcommerceSimulation extends Simulation {

  // URL cible (surchargeable : mvn gatling:test -DbaseUrl=https://...)
  private static final String BASE_URL =
      System.getProperty("baseUrl", "https://api-ecomm.gatling.io");

  private static final int USERS = Integer.getInteger("users", 10);
  private static final int RAMP = Integer.getInteger("ramp", 20);
  private static final double RATE = Double.parseDouble(System.getProperty("rate", "2"));
  private static final int DURATION = Integer.getInteger("duration", 30);

  HttpProtocolBuilder httpProtocol = http
      .baseUrl(BASE_URL)
      .acceptHeader("application/json")
      .userAgentHeader("Gatling/LoadTest");

  FeederBuilder<String> searchFeeder = csv("data/search.csv").random();

  ScenarioBuilder browse = scenario("Recherche et consultation produit")
      .exec(http("Session")
          .get("/session")
          .check(status().is(200))
          .check(jsonPath("$.sessionId").saveAs("sessionId")))
      .pause(1, 3)
      .feed(searchFeeder)
      .exec(http("Recherche")
          .get("/products")
          .queryParam("page", "0")
          .queryParam("search", "#{searchTerm}")
          .check(status().is(200))
          .check(jsonPath("$.products[*].id").findRandom().saveAs("productId")))
      .pause(1, 2)
      .exec(http("Fiche produit")
          .get("/products/#{productId}")
          .check(status().is(200))
          .check(jsonPath("$.id").isEL("#{productId}")));

  {
    setUp(
        browse.injectOpen(
            nothingFor(Duration.ofSeconds(2)),
            rampUsers(USERS).during(Duration.ofSeconds(RAMP)),
            constantUsersPerSec(RATE).during(Duration.ofSeconds(DURATION))))
        .protocols(httpProtocol)
        .maxDuration(Duration.ofMinutes(30))
        .assertions(
            global().responseTime().percentile(95.0).lt(2000),
            global().successfulRequests().percent().gt(95.0));
  }
}
