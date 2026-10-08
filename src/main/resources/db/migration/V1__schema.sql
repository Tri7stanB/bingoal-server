-- Banque de propositions : chaque case = un type de règle + des paramètres.
-- Ajouter une proposition se fait ici (ou par l'admin), sans republier le serveur.
CREATE TABLE proposition (
    id         TEXT PRIMARY KEY,
    label      TEXT    NOT NULL,
    level      TEXT    NOT NULL CHECK (level IN ('FACILE', 'MOYENNE', 'DIFFICILE')),
    rule_type  TEXT    NOT NULL,
    params     JSONB   NOT NULL DEFAULT '{}',
    points     INT     NOT NULL,
    active     BOOLEAN NOT NULL DEFAULT TRUE
);

-- Matchs de la sélection de la semaine. id = id du match chez le fournisseur de données.
CREATE TABLE match (
    id             BIGINT PRIMARY KEY,
    home_team      TEXT,
    away_team      TEXT,
    kickoff        TIMESTAMPTZ NOT NULL,
    status         TEXT NOT NULL DEFAULT 'SCHEDULED' CHECK (status IN ('SCHEDULED', 'LIVE', 'FINISHED', 'CLOSED')),
    api_status     TEXT,
    minute         INT,
    score_home     INT,
    score_away     INT,
    finished_at    TIMESTAMPTZ,
    last_polled_at TIMESTAMPTZ
);

-- Événements bruts, remplacés à chaque passage du poller par la liste complète :
-- un but annulé par la VAR disparaît et les cases sont recalculées.
CREATE TABLE match_event (
    match_id    BIGINT NOT NULL REFERENCES match (id) ON DELETE CASCADE,
    seq         INT    NOT NULL,
    minute      INT    NOT NULL,
    extra       INT,
    team_id     BIGINT,
    team_name   TEXT,
    player_id   BIGINT,
    player_name TEXT,
    type        TEXT   NOT NULL,
    detail      TEXT,
    comments    TEXT,
    PRIMARY KEY (match_id, seq)
);

-- État de chaque proposition pour un match, calculé une fois par match (pas par joueur).
CREATE TABLE proposition_state (
    match_id         BIGINT NOT NULL REFERENCES match (id) ON DELETE CASCADE,
    proposition_id   TEXT   NOT NULL REFERENCES proposition (id),
    state            TEXT   NOT NULL CHECK (state IN ('PENDING', 'VALIDATED', 'FAILED')),
    validated_minute INT,
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (match_id, proposition_id)
);
