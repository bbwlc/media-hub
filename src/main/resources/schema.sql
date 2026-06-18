
DROP TABLE IF EXISTS shared_file;
DROP TABLE IF EXISTS user_profile;
DROP TABLE IF EXISTS account;

CREATE TABLE account (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    username      VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(50)  NOT NULL DEFAULT 'ROLE_USER',
    token         VARCHAR(255) DEFAULT NULL
);

CREATE TABLE user_profile (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    account_id      BIGINT       NOT NULL UNIQUE,
    email           VARCHAR(255),
    profile_picture VARCHAR(255) DEFAULT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'UNVERIFIED',
    FOREIGN KEY (account_id) REFERENCES account(id)
);

CREATE TABLE shared_file (
    id         BIGINT PRIMARY KEY AUTO_INCREMENT,
    owner      VARCHAR(255) NOT NULL,
    filename   VARCHAR(255) NOT NULL,
    shared_with VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);