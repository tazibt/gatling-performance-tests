# Projet Gatling (Java + Maven)

Tests de charge avec Gatling 3.13 (DSL Java, JDK 21).

## Structure

```
src/test/java/simulations/BasicSimulation.java   # scénario de charge
src/test/resources/data/search.csv               # données (feeder)
```

## Lancer

```bash
mvn gatling:test                                   # lance la simulation
mvn gatling:test -DbaseUrl=https://mon-api.local   # autre URL cible
```

Le rapport HTML est généré dans `target/gatling/<simulation>-<date>/index.html`.
