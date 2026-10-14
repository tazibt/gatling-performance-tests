package simulations;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

import io.gatling.javaapi.core.*;
import io.gatling.javaapi.http.*;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

// Tir de charge sur mock-payment-api (https://github.com/tazibt/mock-payment-api)
public class PaymentSimulation extends Simulation {

  private static final String BASE_URL = System.getProperty("baseUrl", "http://localhost:8085");

  // open = débit d'arrivée (rate), closed = utilisateurs simultanés (users)
  private static final String MODEL = System.getProperty("model", "open");
  private static final double RATE = Double.parseDouble(System.getProperty("rate", "20"));
  private static final int USERS = Integer.getInteger("users", 20);
  private static final int RAMP = Integer.getInteger("ramp", 30);
  private static final int DURATION = Integer.getInteger("duration", 60);
  private static final int P95_MAX = Integer.getInteger("p95", 1000);

  HttpProtocolBuilder httpProtocol = http
      .baseUrl(BASE_URL)
      .contentTypeHeader("application/json")
      .acceptHeader("application/json");

  // utilisateurs générés avec Generateur-Data
  FeederBuilder<String> users = csv("data/users.csv").circular();

  ChainBuilder paiementAccepte = exec(
      http("Paiement carte valide")
          .post("/payment/mock")
          .body(StringBody("""
              {"amount": #{amount}, "cardType": "#{card}", "customerId": "#{email}"}"""))
          .check(status().is(200))
          .check(jsonPath("$.status").is("ACCEPTED"))
          .check(jsonPath("$.transactionId").exists()));

  ChainBuilder paiementRefuse = exec(
      http("Paiement carte expirée")
          .post("/payment/mock")
          .body(StringBody("""
              {"amount": #{amount}, "cardType": "EXPIRED", "customerId": "#{email}"}"""))
          .check(status().is(200))
          .check(jsonPath("$.status").is("DECLINED"))
          .check(jsonPath("$.errorCode").is("CARD_EXPIRED")));

  ScenarioBuilder paiement = scenario("Paiement")
      .feed(users)
      .exec(session -> session
          .set("amount", ThreadLocalRandom.current().nextInt(5, 500) + ".90")
          .set("card", ThreadLocalRandom.current().nextBoolean() ? "VISA" : "MASTERCARD"))
      .randomSwitch().on(
          percent(95.0).then(paiementAccepte),
          percent(5.0).then(paiementRefuse));

  {
    PopulationBuilder population = MODEL.equals("closed")
        ? paiement.injectClosed(
            rampConcurrentUsers(0).to(USERS).during(Duration.ofSeconds(RAMP)),
            constantConcurrentUsers(USERS).during(Duration.ofSeconds(DURATION)))
        : paiement.injectOpen(
            rampUsersPerSec(1).to(RATE).during(Duration.ofSeconds(RAMP)),
            constantUsersPerSec(RATE).during(Duration.ofSeconds(DURATION)));

    setUp(population)
        .protocols(httpProtocol)
        .maxDuration(Duration.ofMinutes(30))
        .assertions(
            global().responseTime().percentile(95.0).lt(P95_MAX),
            global().failedRequests().percent().lt(1.0),
            details("Paiement carte valide").responseTime().percentile(99.0).lt(P95_MAX));
  }
}
