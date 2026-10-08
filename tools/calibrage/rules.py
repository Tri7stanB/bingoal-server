"""Moteur de règles Bingoal (prototype).

Chaque proposition de la banque = un type de règle + des paramètres.
evaluate() calcule l'état d'une proposition pour un match à partir de la
liste COMPLÈTE de ses événements : si la VAR annule un but et que l'API
retire l'événement, un nouveau calcul corrige la case tout seul.

États : "validee", "en_attente" (peut encore arriver), "ratee" (plus possible).
Le match est au format renvoyé par API-Football /fixtures (un élément de "response").
"""

FINISHED = {"FT", "AET", "PEN", "AWD", "WO"}


# ---------- Lecture du match ----------

def minute(ev):
    return ev["time"]["elapsed"] or 0


def in_regular_time(ev):
    # Exclut prolongations (> 90+arrêts) et tirs au but.
    comments = (ev.get("comments") or "").lower()
    return minute(ev) <= 90 and "shootout" not in comments


def events(m):
    return [e for e in m.get("events", []) if in_regular_time(e)]


def goals(m):
    """Buts comptés (penaltys marqués et c.s.c. inclus, penaltys ratés exclus)."""
    return [e for e in events(m) if e["type"] == "Goal" and e["detail"] != "Missed Penalty"]


def is_finished(m):
    return m["fixture"]["status"]["short"] in FINISHED


def elapsed(m):
    return m["fixture"]["status"].get("elapsed") or 0


def score_90(m):
    ft = (m.get("score") or {}).get("fulltime") or {}
    if ft.get("home") is not None:
        return ft["home"], ft["away"]
    return m["goals"]["home"] or 0, m["goals"]["away"] or 0


def starters(m):
    return {p["player"]["id"] for t in m.get("lineups", []) for p in t.get("startXI", [])}


def stat_total(m, name):
    total = None
    for team in m.get("statistics", []):
        for s in team["statistics"]:
            if s["type"] == name and s["value"] is not None:
                total = (total or 0) + int(str(s["value"]).rstrip("%"))
    return total


# ---------- Types de règles ----------
# Chaque fonction renvoie True si la case est validée.
# Les règles « avant la minute X » peuvent devenir ratées avant la fin du match.

def total_goals_over(m, seuil):
    h, a = score_90(m)
    return h + a > seuil


def both_teams_score(m):
    h, a = score_90(m)
    return h > 0 and a > 0


def goal_before_minute(m, minute_max):
    return any(minute(e) < minute_max for e in goals(m))


def stat_total_over(m, stat, seuil):
    total = stat_total(m, stat)
    return total is not None and total > seuil


def penalty_scored(m):
    # L'API ne publie pas les penaltys ratés : seuls les penaltys marqués sont fiables.
    return any(e["type"] == "Goal" and e["detail"] == "Penalty" for e in events(m))


def var_event(m):
    return any(e["type"] == "Var" for e in events(m))


def goal_in_stoppage_time(m):
    return any((e["time"].get("extra") or 0) > 0 for e in goals(m))


def substitute_scores(m):
    xi = starters(m)
    return bool(xi) and any(e["detail"] != "Own Goal" and e["player"]["id"] not in xi for e in goals(m))


def red_card(m):
    # Un carton rouge annulé par la VAR (« Red card cancelled ») ne compte pas.
    reds = sum(1 for e in events(m) if e["type"] == "Card" and e["detail"] in ("Red Card", "Second Yellow card"))
    cancelled = sum(1 for e in events(m) if e["type"] == "Var" and e["detail"] == "Red card cancelled")
    return reds - cancelled > 0


def player_scores_n(m, n):
    count = {}
    for e in goals(m):
        if e["detail"] != "Own Goal":
            pid = e["player"]["id"]
            count[pid] = count.get(pid, 0) + 1
    return any(c >= n for c in count.values())


def own_goal(m):
    return any(e["detail"] == "Own Goal" for e in goals(m))



RULES = {
    "total_goals_over": lambda m, p: total_goals_over(m, p["seuil"]),
    "both_teams_score": lambda m, p: both_teams_score(m),
    "goal_before_minute": lambda m, p: goal_before_minute(m, p["minute"]),
    "stat_total_over": lambda m, p: stat_total_over(m, p["stat"], p["seuil"]),
    "penalty_scored": lambda m, p: penalty_scored(m),
    "var_event": lambda m, p: var_event(m),
    "goal_in_stoppage_time": lambda m, p: goal_in_stoppage_time(m),
    "substitute_scores": lambda m, p: substitute_scores(m),
    "red_card": lambda m, p: red_card(m),
    "player_scores_n": lambda m, p: player_scores_n(m, p["n"]),
    "own_goal": lambda m, p: own_goal(m),
}


def evaluate(prop, m):
    """État d'une proposition de la banque pour un match (fini ou en cours)."""
    if RULES[prop["regle"]](m, prop["params"]):
        return "validee"
    if is_finished(m):
        return "ratee"
    if prop["regle"] == "goal_before_minute" and elapsed(m) >= prop["params"]["minute"]:
        return "ratee"
    return "en_attente"
