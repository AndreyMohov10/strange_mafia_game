CREATE TABLE IF NOT EXISTS game_configs (
    game_id VARCHAR(40) NOT NULL PRIMARY KEY,
    players_num INTEGER NOT NULL,
    logging_timeout BIGINT NOT NULL,
    turn_timeout BIGINT NOT NULL,
    regain_chance DOUBLE PRECISION NOT NULL,
    agents INTEGER NOT NULL,
    team INTEGER NOT NULL,
    check_chance DOUBLE PRECISION NOT NULL,
    artifacts INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS game_states (
    game_id VARCHAR(40) NOT NULL PRIMARY KEY,
    players_id JSONB NOT NULL,
    roles JSONB NOT NULL,
    stolen_artifacts JSONB NOT NULL,
    alive JSONB NOT NULL,
    corrupted JSONB NOT NULL,
    phase VARCHAR(20) NOT NULL,
    day INTEGER NOT NULL,
    player INTEGER NOT NULL,
    history JSONB NOT NULL,
    CONSTRAINT fk_game_states_game_configs FOREIGN KEY (game_id) REFERENCES game_configs(game_id) ON DELETE CASCADE
);
