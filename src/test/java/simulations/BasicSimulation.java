package simulations;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

import io.gatling.javaapi.core.*;
import io.gatling.javaapi.http.*;
import java.time.Duration;

public class BasicSimulation extends Simulation {

  // URL cible (surchargeable : mvn gatling:test -DbaseUrl=https://...)
  private static final String BASE_URL =
      System.getProperty("baseUrl", "https://computer-database.gatling.io");

  HttpProtocolBuilder httpProtocol = http
      .baseUrl(BASE_URL)
      .acceptHeader("text/html,application/json")
      .userAgentHeader("Gatling/LoadTest");

  FeederBuilder<String> searchFeeder = csv("data/search.csv").random();

  ScenarioBuilder browse = scenario("Navigation et recherche")
      .exec(http("Accueil").get("/").check(status().is(200)))
      .pause(1, 3)
      .feed(searchFeeder)
      .exec(http("Recherche #{searchTerm}")
          .get("/computers")
          .queryParam("f", "#{searchTerm}")
          .check(status().is(200)))
      .pause(1, 2);

  {
    setUp(
        browse.injectOpen(
            nothingFor(Duration.ofSeconds(2)),
            rampUsers(10).during(Duration.ofSeconds(20)),
            constantUsersPerSec(2).during(Duration.ofSeconds(30))))
        .protocols(httpProtocol)
        .assertions(
            global().responseTime().percentile(95.0).lt(2000),
            global().successfulRequests().percent().gt(95.0));
  }
}
