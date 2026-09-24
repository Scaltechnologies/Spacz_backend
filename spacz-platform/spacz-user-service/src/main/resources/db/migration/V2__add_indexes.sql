CREATE INDEX idx_user_preparations_user ON user_preparations (user_id);
CREATE INDEX idx_user_activities_user_created ON user_activities (user_id, created_at DESC);
CREATE INDEX idx_preparation_programs_category ON preparation_programs (category) WHERE active;
