#!/usr/bin/env python3
"""Bingoal, étape 1 : vérifier qu'API-Football permet de valider les cases.

Usage :
  export APIFOOTBALL_KEY=ta_cle
  python3 test_cases.py --find 2023-05-21 --league 61 --season 2022   # liste les matchs du jour (saison = année de début)
  python3 test_cases.py --fixture 971414                               # teste un match
  python3 test_cases.py --file match_971414.json                       # rejoue un JSON déjà téléchargé (0 requête)

Un appel /fixtures?id=X renvoie en une seule requête le score, les événements,
les compositions et les statistiques. Le JSON brut est sauvegardé pour
pouvoir rejouer le match sans consommer de requêtes (100/jour en gratuit).

Python 3.8+ sans dépendance.
"""
import argparse
import json
import os
import sys
import urllib.parse
import urllib.request

BASE = "https://v3.football.api-sports.io"

# Ligues utiles : 61 Ligue 1, 39 Premier League, 140 Liga, 135 Serie A, 78 Bundesliga, 2 Ligue des champions


def call(path, params):
    key = os.environ.get("APIFOOTBALL_KEY")
    if not key:
        sys.exit("Définis APIFOOTBALL_KEY (clé gratuite sur dashboard.api-football.com).")
    url = f"{BASE}{path}?{urllib.parse.urlencode(params)}"
    req = urllib.request.Request(url, headers={"x-apisports-key": key})
    with urllib.request.urlopen(req, timeout=30) as r:
        data = json.load(r)
        remaining = r.headers.get("x-ratelimit-requests-remaining")
    if data.get("errors"):
        sys.exit(f"Erreur API : {data['errors']}")
    if remaining is not None:
        print(f"(requêtes restantes aujourd'hui : {remaining})")
    return data


def find(date, league, season):
    # Le plan gratuit refuse le filtre par date hors des jours courants :
    # on récupère toute la saison en une requête et on filtre ici.
    data = call("/fixtures", {"league": league, "season": season})
    found = [f for f in data["response"] if f["fixture"]["date"].startswith(date)]
    for f in found:
        fx, t, g = f["fixture"], f["teams"], f["goals"]
        print(f"{fx['id']:>8}  {t['home']['name']} {g['home']}-{g['away']} {t['away']['name']}  ({fx['status']['short']})")
    if not found:
        print(f"Aucun match le {date} ({len(data['response'])} matchs dans la saison). Essaie un autre jour.")


# ---------- Lecture des événements ----------

def minute(ev):
    """Minute de jeu ; les arrêts de jeu de la 1re mi-temps comptent comme 45."""
    return ev["time"]["elapsed"] or 0


def is_shootout(ev):
    # Les tirs au but arrivent comme des événements « Goal » après la 120e.
    return (ev.get("comments") or "").lower().startswith("penalty shootout") or minute(ev) > 120


def goals(events):
    return [e for e in events if e["type"] == "Goal" and e["detail"] != "Missed Penalty" and not is_shootout(e)]


def var_events(events):
    return [e for e in events if e["type"] == "Var"]


def cancelled_goal_minutes(events):
    return {minute(e) for e in var_events(events) if "cancel" in (e["detail"] or "").lower()}


def starters(match):
    ids = set()
    for team in match.get("lineups", []):
        for p in team.get("startXI", []):
            ids.add(p["player"]["id"])
    return ids


def corner_total(match):
    total = None
    for team in match.get("statistics", []):
        for s in team["statistics"]:
            if s["type"] == "Corner Kicks" and s["value"] is not None:
                total = (total or 0) + int(s["value"])
    return total


# ---------- Les cases de la banque de départ ----------
# Chaque case renvoie (résultat, détectable, explication).
# résultat : True / False / None (impossible à dire avec les données)

def c_goal_before_20(m, ev):
    g = [e for e in goals(ev) if minute(e) < 20]
    note = ""
    if cancelled_goal_minutes(ev):
        note = f" ; but(s) annulé(s) par la VAR à {sorted(cancelled_goal_minutes(ev))}' : vérifier qu'ils ne sont pas comptés"
    return bool(g), "oui", f"{len(g)} but(s) avant la 20e{note}"


def c_penalty(m, ev):
    p = [e for e in ev if e["type"] == "Goal" and e["detail"] in ("Penalty", "Missed Penalty") and not is_shootout(e)]
    details = ", ".join(f"{minute(e)}' {e['detail']}" for e in p)
    return bool(p), "oui", details or "aucun penalty (réussi ou raté) dans les événements"


def c_red_card(m, ev):
    r = [e for e in ev if e["type"] == "Card" and e["detail"] in ("Red Card", "Second Yellow card")]
    return bool(r), "oui", ", ".join(f"{minute(e)}' {e['player']['name']} ({e['detail']})" for e in r) or "aucun"


def c_corner_before_10(m, ev):
    total = corner_total(m)
    return None, "non (en différé)", (
        f"les corners ne sont pas des événements, seulement un total dans les stats ({total} au total). "
        "En direct, le poller pourrait noter la minute où le total passe de 0 à 1 : à tester sur un match en cours."
    )


def c_header_goal(m, ev):
    return None, "non", "la partie du corps n'apparaît pas dans les événements de but ; case à retirer ou à valider à la main"


def c_var_used(m, ev):
    v = var_events(ev)
    return bool(v), "oui, selon la ligue", ", ".join(f"{minute(e)}' {e['detail']}" for e in v) or "aucun événement VAR (peut aussi vouloir dire que la ligue n'est pas couverte)"


def c_over_2_5(m, ev):
    h, a = m["goals"]["home"], m["goals"]["away"]
    if h is None:
        return None, "oui", "score indisponible"
    return h + a > 2, "oui", f"score {h}-{a}"


def c_sub_scorer(m, ev):
    xi = starters(m)
    if not xi:
        return None, "non", "pas de compositions pour ce match"
    s = [e for e in goals(ev) if e["detail"] != "Own Goal" and e["player"]["id"] not in xi]
    return bool(s), "oui", ", ".join(f"{minute(e)}' {e['player']['name']}" for e in s) or "aucun remplaçant buteur"


CASES = [
    ("But avant la 20e minute", "facile", c_goal_before_20),
    ("Penalty sifflé", "moyenne", c_penalty),
    ("Carton rouge", "difficile", c_red_card),
    ("Corner avant la 10e", "facile", c_corner_before_10),
    ("But de la tête", "moyenne", c_header_goal),
    ("VAR utilisée", "moyenne", c_var_used),
    ("Plus de 2,5 buts", "facile", c_over_2_5),
    ("Remplaçant buteur", "difficile", c_sub_scorer),
]


def report(match):
    fx, t, g = match["fixture"], match["teams"], match["goals"]
    ev = sorted(match.get("events", []), key=lambda e: (minute(e), e["time"].get("extra") or 0))
    print(f"\n{t['home']['name']} {g['home']}-{g['away']} {t['away']['name']}  (match {fx['id']}, {fx['status']['long']})")
    print(f"{len(ev)} événements, compositions : {'oui' if match.get('lineups') else 'non'}, stats : {'oui' if match.get('statistics') else 'non'}\n")
    print("Chronologie :")
    for e in ev:
        extra = f"+{e['time']['extra']}" if e["time"].get("extra") else ""
        print(f"  {minute(e):>3}{extra:<3} {e['type']:<6} {e['detail'] or '':<22} {e['team']['name']:<20} {e['player']['name'] or ''}")
    print("\nCases :")
    for label, level, fn in CASES:
        res, detectable, why = fn(match, ev)
        mark = {True: "VALIDÉE", False: "ratée", None: "  ?"}[res]
        print(f"  [{mark:^8}] {label:<26} ({level}) détectable : {detectable}\n             {why}")


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--find", metavar="AAAA-MM-JJ")
    ap.add_argument("--league", type=int, default=61)
    ap.add_argument("--season", type=int)
    ap.add_argument("--fixture", type=int)
    ap.add_argument("--file")
    a = ap.parse_args()

    if a.find:
        find(a.find, a.league, a.season or (int(a.find[:4]) if int(a.find[5:7]) >= 7 else int(a.find[:4]) - 1))
    elif a.fixture:
        data = call("/fixtures", {"id": a.fixture})
        if not data["response"]:
            sys.exit("Match introuvable (ou saison non couverte par ton plan).")
        out = f"match_{a.fixture}.json"
        with open(out, "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False, indent=1)
        print(f"JSON brut sauvegardé dans {out}")
        report(data["response"][0])
    elif a.file:
        with open(a.file, encoding="utf-8") as f:
            report(json.load(f)["response"][0])
    else:
        ap.print_help()


if __name__ == "__main__":
    main()
