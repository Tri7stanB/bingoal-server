-- Banque de départ, calibrée sur 105 matchs de Ligue 1 2022-23 (voir tools/calibrage).
-- Points : 10 / 20 / 30 selon le niveau, soit environ 5 points espérés par case.
INSERT INTO proposition (id, label, level, rule_type, params, points) VALUES
    ('over_2_5', 'Plus de 2,5 buts', 'FACILE', 'TOTAL_GOALS_OVER', '{"threshold": 2.5}', 10),
    ('btts', 'Les deux équipes marquent', 'FACILE', 'BOTH_TEAMS_SCORE', '{}', 10),
    ('goal_before_25', 'But avant la 25e minute', 'FACILE', 'GOAL_BEFORE_MINUTE', '{"minute": 25}', 10),
    ('corners_over_9', 'Plus de 9 corners dans le match', 'FACILE', 'STAT_TOTAL_OVER', '{"stat": "Corner Kicks", "threshold": 9.5}', 10),
    ('penalty', 'Penalty marqué', 'MOYENNE', 'PENALTY_SCORED', '{}', 20),
    ('var', 'Intervention de la VAR', 'MOYENNE', 'VAR_EVENT', '{}', 20),
    ('sub_scorer', 'Un remplaçant marque', 'MOYENNE', 'SUBSTITUTE_SCORES', '{}', 20),
    ('red_card', 'Carton rouge', 'MOYENNE', 'RED_CARD', '{}', 20),
    ('stoppage_goal', 'But dans les arrêts de jeu', 'DIFFICILE', 'GOAL_IN_STOPPAGE_TIME', '{}', 30),
    ('brace', 'Un joueur marque un doublé', 'DIFFICILE', 'PLAYER_SCORES_N', '{"n": 2}', 30),
    ('own_goal', 'But contre son camp', 'DIFFICILE', 'OWN_GOAL', '{}', 30),
    ('over_4_5', 'Plus de 4,5 buts', 'DIFFICILE', 'TOTAL_GOALS_OVER', '{"threshold": 4.5}', 30);
