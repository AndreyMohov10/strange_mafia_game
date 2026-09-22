CREATE TABLE IF NOT EXISTS users (
    id VARCHAR(255) NOT NULL PRIMARY KEY,
    username VARCHAR(255) NOT NULL,
    token VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS game_events (
    game_id VARCHAR(255) NOT NULL,
    event_index INTEGER NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    PRIMARY KEY (game_id, event_index)
);

CREATE INDEX IF NOT EXISTS idx_users_token ON users (token);
CREATE INDEX IF NOT EXISTS idx_game_events_game_id_event_index ON game_events (game_id, event_index);
