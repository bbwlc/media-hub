
INSERT INTO account (username, password_hash, role) VALUES
('user1', '$2a$12$Q01Gsg2yq9rTzKOIDFJqEeBakU0TpUU/JPaavprd8ZMoIUzg8kt1K', 'ROLE_ADMIN'), -- Passwort: password123
('user2', '$2a$12$tGUuloNZ15UJ6hhTMqLRDeIoNNP.hg/73V0VCXz/s/SPTE.WoQnLe', 'ROLE_USER'),  -- Passwort: 123456
('user3', '$2a$12$tGUuloNZ15UJ6hhTMqLRDeIoNNP.hg/73V0VCXz/s/SPTE.WoQnLe', 'ROLE_USER');  -- Passwort: 123456

INSERT INTO user_profile (account_id, email, status) VALUES
(1, NULL, 'VERIFIED'),
(2, NULL, 'VERIFIED'),
(3, NULL, 'UNVERIFIED');