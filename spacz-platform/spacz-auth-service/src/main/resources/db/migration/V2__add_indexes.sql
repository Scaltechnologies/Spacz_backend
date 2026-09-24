CREATE INDEX idx_user_accounts_role_status ON user_accounts (role, status);
CREATE INDEX idx_user_accounts_created_at ON user_accounts (created_at DESC);
CREATE INDEX idx_refresh_tokens_account_active ON refresh_tokens (user_account_id) WHERE revoked_at IS NULL;
CREATE INDEX idx_refresh_tokens_expires_at ON refresh_tokens (expires_at);
