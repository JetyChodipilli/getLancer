-- Refuse multiple admins, including concurrent inserts. Existing conflicts require explicit resolution.
CREATE UNIQUE INDEX one_administrator ON user_roles (role) WHERE role = 'ADMIN';
