# bingoal-server

Serveur de Bingoal : il suit les matchs de la sélection de la semaine, récupère leurs
événements chez le fournisseur de données (API-Football) et calcule en direct l'état
de chaque case de la banque (validée, en attente, ratée).

Les apps ne parlent jamais au fournisseur de données, seulement à ce serveur :
le nombre d'appels dépend du nombre de matchs, pas du nombre de joueurs.

## Ce qu'il y a dedans

| Partie | Rôle |
| --- | --- |
| `football/` | Accès aux données : `ApiFootballDataSource` (vrais matchs) ou `ReplayDataSource` (rejoue un match déjà joué depuis un fichier JSON, sans consommer de requêtes). |
| `rules/` | Moteur de règles : chaque proposition = un type de règle + des paramètres. L'état est recalculé à chaque passage depuis la liste complète des événements, donc un but annulé par la VAR corrige la case tout seul. |
| `match/MatchPoller` | Seule partie qui appelle le fournisseur, de 5 min avant le coup d'envoi à 10 min après le coup de sifflet final. |
| `db/migration` | Schéma PostgreSQL (Flyway) et banque de départ (12 cases, 10/20/30 points). |
| `tools/` | Scripts Python des étapes 1 et 2 : test d'API-Football sur un match et calibrage de la banque sur une saison. |

## Lancer en local (WSL / Linux)

Prérequis : Java 21 (`sudo apt install openjdk-21-jdk`) et une base PostgreSQL.

```bash
# Base de données : avec Docker...
docker compose up -d
# ...ou sans Docker
sudo apt install postgresql && sudo service postgresql start
sudo -u postgres psql -c "CREATE USER bingoal PASSWORD 'bingoal'" -c "CREATE DATABASE bingoal OWNER bingoal"

./gradlew test       # tests du moteur de règles et du rejeu
./gradlew bootRun    # démarre sur http://localhost:8080
```

## Rejouer un match déjà joué

Par défaut le serveur est en mode `replay`. Copie un JSON de match (par exemple
`match_871824.json` produit par `tools/etape1/test_cases.py`) dans `replays/`, puis :

```bash
# ajoute le match à la sélection (coup d'envoi = maintenant)
curl -X POST localhost:8080/admin/matches -H 'X-Admin-Token: dev' \
     -H 'Content-Type: application/json' -d '{"fixtureId": 871824}'

# toutes les 30 s le poller avance le match de 5 minutes ; pour aller plus vite :
curl -X POST localhost:8080/admin/matches/871824/poll -H 'X-Admin-Token: dev'

# état du match et des 12 cases
curl localhost:8080/matches/871824
```

Variables utiles : `BINGOAL_POLL_INTERVAL=2s`, `BINGOAL_REPLAY_STEP=10` (minutes par passage).

## Vrais matchs

```bash
BINGOAL_DATA_SOURCE=api-football APIFOOTBALL_KEY=ta_cle ./gradlew bootRun
```

Le plan gratuit d'API-Football ne donne accès qu'aux saisons 2022 à 2024 et à
10 requêtes par minute : pour suivre un match en cours, il faut un plan payant.

## Endpoints

| Méthode | Chemin | Rôle |
| --- | --- | --- |
| GET | `/matches` | Matchs de la sélection |
| GET | `/matches/{id}` | Match, état des cases, événements |
| POST | `/admin/matches` | Ajoute un match (`fixtureId`, `kickoff` optionnel) |
| DELETE | `/admin/matches/{id}` | Retire un match |
| POST | `/admin/matches/{id}/poll` | Force un passage du poller |

Les endpoints `/admin` demandent l'en-tête `X-Admin-Token` (`BINGOAL_ADMIN_TOKEN`, `dev` par défaut).
