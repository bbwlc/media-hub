
INSERT INTO account (username, password_hash, role, status) VALUES
('user1', '$2a$12$Q01Gsg2yq9rTzKOIDFJqEeBakU0TpUU/JPaavprd8ZMoIUzg8kt1K', 'ROLE_ADMIN', 'VERIFIED'),   -- Passwort: password123
('user2', '$2a$12$tGUuloNZ15UJ6hhTMqLRDeIoNNP.hg/73V0VCXz/s/SPTE.WoQnLe', 'ROLE_USER',  'VERIFIED'),   -- Passwort: 123456
('user3', '$2a$12$tGUuloNZ15UJ6hhTMqLRDeIoNNP.hg/73V0VCXz/s/SPTE.WoQnLe', 'ROLE_USER',  'UNVERIFIED'); -- Passwort: 123456
