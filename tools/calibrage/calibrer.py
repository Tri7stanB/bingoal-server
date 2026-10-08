#!/usr/bin/env python3
"""Bingoal, étape 2 : calibrer la banque sur une vraie saison.

Télécharge les matchs d'une saison (1 requête par match en plan gratuit,
environ 95 par jour, 10 par minute, mis en cache sur le disque ; compter ~10 min), puis calcule pour chaque proposition de banque.json la part
des matchs où elle aurait été validée. On en déduit le niveau et les points.

Usage :
  export APIFOOTBALL_KEY=ta_cle
  python3 calibrer.py --league 61 --season 2022          # Ligue 1 2022-23 : 95 matchs par jour
  python3 calibrer.py --league 61 --season 2022 --offline # recalcule sans requête
  python3 calibrer.py --dir ../etape1                     # évalue des match_*.json de l'étape 1

Relancer la commande reprend là où le téléchargement s'est arrêté.
Python 3.8+ sans dépendance. banque.json et rules.py doivent être dans le même dossier.
"""
import argparse
import glob
import json
import os
import random
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import rules  # noqa: E402

BASE = "https://v3.football.api-sports.io"
PAUSE = 6.5  # plan gratuit : 10 requêtes par minute maximum


def call(path, params):
    key = os.environ.get("APIFOOTBALL_KEY")
    if not key:
        sys.exit("Définis APIFOOTBALL_KEY.")
    url = f"{BASE}{path}?{urllib.parse.urlencode(params)}"
    req = urllib.request.Request(url, headers={"x-apisports-key": key})
    for attempt in range(3):
        try:
            with urllib.request.urlopen(req, timeout=60) as r:
                data = json.load(r)
                remaining = r.headers.get("x-ratelimit-requests-remaining")
            break
        except urllib.error.HTTPError as e:
            if e.code != 429 or attempt == 2:
                raise
            print("  limite par minute atteinte, pause de 60 s...")
            time.sleep(60)
    time.sleep(PAUSE)
    if data.get("errors"):
        sys.exit(f"Erreur API : {data['errors']}")
    return data, remaining


def download(league, season, cache, max_requests):
    os.makedirs(cache, exist_ok=True)
    index = os.path.join(cache, "_liste.json")
    used = 0
    if not os.path.exists(index):
        data, remaining = call("/fixtures", {"league": league, "season": season})
        used += 1
        ids = [f["fixture"]["id"] for f in data["response"] if f["fixture"]["status"]["short"] in rules.FINISHED]
        with open(index, "w") as f:
            json.dump(ids, f)
    with open(index) as f:
        ids = json.load(f)
    todo = [i for i in ids if not os.path.exists(os.path.join(cache, f"{i}.json"))]
    print(f"{len(ids)} matchs terminés dans la saison, {len(todo)} à télécharger.")
    # Le plan gratuit refuse ?ids= : une requête par match. Ordre mélangé (graine fixe)
    # pour qu'un échantillon partiel couvre toute la saison.
    random.Random(0).shuffle(todo)
    while todo and used < max_requests:
        fid = todo.pop(0)
        data, remaining = call("/fixtures", {"id": fid})
        used += 1
        for m in data["response"]:
            with open(os.path.join(cache, f"{fid}.json"), "w", encoding="utf-8") as f:
                json.dump(m, f, ensure_ascii=False)
        if remaining is not None and int(remaining) <= 1:
            print("Quota du jour presque épuisé, arrêt.")
            break
        if used % 10 == 0:
            print(f"  {used} requêtes faites (restantes aujourd'hui : {remaining})")
    if todo:
        print(f"Arrêt après {used} requêtes : {len(todo)} matchs restants, relance la commande demain pour compléter.")


def load(folder):
    matches = []
    for path in sorted(glob.glob(os.path.join(folder, "*.json"))):
        if os.path.basename(path).startswith("_"):
            continue
        with open(path, encoding="utf-8") as f:
            data = json.load(f)
        matches.extend(data["response"] if "response" in data else [data])
    return matches


def suggest(p):
    level = "facile" if p >= 0.40 else "moyenne" if p >= 0.20 else "difficile"
    points = min(30, max(5, 5 * round(1 / max(p, 0.01))))  # ~5 points x rareté, arrondi à 5
    return level, points


def report(matches, bank):
    n = len(matches)
    if not n:
        sys.exit("Aucun match à évaluer.")
    with_var = sum(1 for m in matches if rules.var_event(m))
    with_lineups = sum(1 for m in matches if m.get("lineups"))
    with_stats = sum(1 for m in matches if m.get("statistics"))
    mismatch = sum(1 for m in matches if len(rules.goals(m)) != sum(rules.score_90(m)))
    print(f"\nContrôle : {mismatch} match(s) où les buts des événements ne collent pas au score (ex. but annulé resté dans la liste).")
    print(f"{n} matchs évalués. Couverture : compositions {with_lineups}/{n}, stats {with_stats}/{n}, au moins un événement VAR {with_var}/{n}.\n")
    print(f"{'Proposition':<34}{'Fréquence':>10}   {'Niveau actuel':<14}{'Suggéré':<11}{'Points suggérés':>15}")
    for prop in bank["propositions"]:
        hits = sum(1 for m in matches if rules.evaluate(prop, m) == "validee")
        p = hits / n
        level, points = suggest(p)
        flag = "" if level == prop["niveau"] else "  <- à revoir"
        print(f"{prop['texte']:<34}{p:>9.0%}   {prop['niveau']:<14}{level:<11}{points:>15}{flag}")


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--league", type=int, default=61)
    ap.add_argument("--season", type=int, default=2022)
    ap.add_argument("--max-requests", type=int, default=95)
    ap.add_argument("--offline", action="store_true")
    ap.add_argument("--dir", help="dossier de JSON de matchs déjà téléchargés")
    a = ap.parse_args()

    with open(os.path.join(HERE, "banque.json"), encoding="utf-8") as f:
        bank = json.load(f)
    if a.dir:
        folder = a.dir
    else:
        folder = os.path.join(HERE, "matchs", f"{a.league}_{a.season}")
        if not a.offline:
            download(a.league, a.season, folder, a.max_requests)
    report(load(folder), bank)


if __name__ == "__main__":
    main()
