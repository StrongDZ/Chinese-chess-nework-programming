-- users & user_stats schema for SIGNUP
CREATE TABLE IF NOT EXISTS users (
  id            CHAR(36)      PRIMARY KEY,
  username      VARCHAR(32)   NOT NULL,
  email         VARCHAR(254)  NOT NULL,
  password_hash VARCHAR(128)  NOT NULL,
  elo           INT           NOT NULL DEFAULT 1200,
  status        VARCHAR(16)   NOT NULL DEFAULT 'OFFLINE',
  created_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  last_login    TIMESTAMP     NULL
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_users_username ON users (LOWER(username));
CREATE UNIQUE INDEX IF NOT EXISTS ux_users_email    ON users (LOWER(email));

CREATE TABLE IF NOT EXISTS user_stats (
  user_id     CHAR(36)  PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
  total_games INT       NOT NULL DEFAULT 0,
  wins        INT       NOT NULL DEFAULT 0,
  losses      INT       NOT NULL DEFAULT 0,
  draws       INT       NOT NULL DEFAULT 0,
  last_game   TIMESTAMP NULL
);
CREATE INDEX IF NOT EXISTS ix_users_elo ON users (elo DESC);
