# gatling-performance-tests

[![Tir de charge](https://github.com/tazibt/gatling-performance-tests/actions/workflows/load-test.yml/badge.svg)](https://github.com/tazibt/gatling-performance-tests/actions/workflows/load-test.yml)

Tests de performance avec **Gatling** (DSL Java, Maven, JDK 21).

Après plusieurs années de tests de charge sous JMeter, ce dépôt reprend les mêmes pratiques
avec Gatling : profils de charge paramétrables, corrélation, jeux de données, assertions de SLA
et exécution en CI.

## Simulations

| Simulation | Cible | Ce qu'elle montre |
|---|---|---|
| `EcommerceSimulation` | API de démo Gatling (`api-ecomm.gatling.io`) | Parcours session → recherche → fiche produit, **corrélation** (`productId` extrait de la recherche et réutilisé), feeder CSV |
| `PaymentSimulation` | [mock-payment-sap](https://github.com/tazibt/mock-payment-sap) en local | Modèles **ouvert / fermé**, données générées par [Generateur-Data](https://github.com/tazibt/Generateur-Data), mix de cas (95 % accepté / 5 % refusé), checks JSON |

```
src/test/java/simulations/
├── EcommerceSimulation.java
└── PaymentSimulation.java
src/test/resources/data/
├── search.csv     # termes de recherche
└── users.csv      # 200 utilisateurs fictifs (example.com)
```

## Lancer un tir

```bash
mvn gatling:test -Dgatling.simulationClass=simulations.EcommerceSimulation
```

Pour `PaymentSimulation`, démarrer d'abord le mock avec une latence réaliste :

```bash
java -jar mock-payment-sap-1.0.0.jar --mock.delay.success=300 --mock.delay.error=300
mvn gatling:test -Dgatling.simulationClass=simulations.PaymentSimulation -Drate=50 -Dduration=60
```

Le rapport HTML est généré dans `target/gatling/<simulation>-<date>/index.html`.

## Paramètres

Tout se passe en ligne de commande, sans toucher au code : le même script sert pour un tir
de validation, de charge ou d'endurance.

| Paramètre | Défaut | Rôle |
|---|---|---|
| `baseUrl` | selon la simulation | URL cible |
| `model` | `open` | `open` = débit d'arrivée, `closed` = utilisateurs simultanés (`PaymentSimulation`) |
| `rate` | `20` | Utilisateurs arrivant par seconde (modèle ouvert) |
| `users` | `20` | Utilisateurs simultanés (modèle fermé) |
| `ramp` | `30` | Durée de montée en charge (s) |
| `duration` | `60` | Durée du plateau (s) |
| `p95` | `1000` | Seuil du 95e percentile (ms) pour l'assertion |

## Modèle ouvert ou fermé ?

- **Ouvert** (`injectOpen`) : on fixe le **débit d'arrivée**. Si l'application ralentit, les utilisateurs
  continuent d'arriver et s'accumulent, comme sur un site public.
- **Fermé** (`injectClosed`) : on fixe le **nombre d'utilisateurs simultanés**, un nouveau remplace
  celui qui termine. Adapté à une population connue (application interne, call center),
  c'est l'équivalent du Thread Group JMeter.

Les deux sont reliés par la loi de Little : **concurrence = débit × temps de réponse**.

## Résultats (poste local, mock à 300 ms de latence)

| Tir | Requêtes | KO | p50 | p95 | p99 | Débit moyen |
|---|---|---|---|---|---|---|
| Ouvert, 50 paiements/s, 60 s de plateau | 3 510 | 0 | 312 ms | 319 ms | 329 ms | 43 req/s |
| Fermé, 50 utilisateurs simultanés, 60 s | 11 178 | 0 | 312 ms | 325 ms | 342 ms | 138 req/s |

Lecture :
- En modèle ouvert, 50 arrivées/s × 0,31 s ≈ **16 paiements en cours** en même temps.
- En modèle fermé, 50 utilisateurs qui enchaînent des requêtes de 0,31 s donnent au maximum
  50 / 0,31 ≈ **160 req/s** sur le plateau ; la moyenne de 138 req/s inclut la montée en charge.
- Le temps de réponse reste collé à la latence simulée (300 ms) : le mock n'est pas le goulot,
  ce qui est le but quand il remplace un prestataire externe dans un tir.

## Intégration continue

Le workflow `Tir de charge` (GitHub Actions) récupère et démarre le mock, lance `PaymentSimulation`
(10 paiements/s par défaut) et publie le rapport Gatling en artefact. Les assertions
(p95, taux d'erreur) font échouer le build si le SLA n'est pas tenu.
Un tir plus long peut être lancé à la main depuis l'onglet *Actions* en choisissant le débit et la durée.

## Licence

MIT – voir [LICENSE](LICENSE).
