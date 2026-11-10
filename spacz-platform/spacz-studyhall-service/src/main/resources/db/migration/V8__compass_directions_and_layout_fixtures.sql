-- 1. Compass directions (map seen from the entrance): NORTH = far side, SOUTH = entrance side,
--    WEST / EAST = left / right. Existing V7 values are converted in place (UP→NORTH ...); nothing else changes.
ALTER TABLE layout_cells DROP CONSTRAINT ck_layout_cells_direction;

UPDATE layout_cells SET direction = CASE direction
    WHEN 'UP' THEN 'NORTH'
    WHEN 'DOWN' THEN 'SOUTH'
    WHEN 'LEFT' THEN 'WEST'
    WHEN 'RIGHT' THEN 'EAST'
    ELSE direction END
WHERE direction IS NOT NULL;

ALTER TABLE layout_cells ADD CONSTRAINT ck_layout_cells_direction CHECK (
    direction IS NULL
    OR (direction IN ('NORTH', 'SOUTH', 'EAST', 'WEST')
        AND cell_type IN ('SEAT', 'ENTRANCE', 'EXIT', 'AC', 'STAIRS', 'LIFT'))
    OR (direction = 'CENTER' AND cell_type = 'AC'));

-- 2. Layout fixtures: environmental annotations (AC ...) on a block edge or its centre. They take no
--    grid cell, so they never add rows and can never become seats. Deleted with the layout.
CREATE TABLE layout_fixtures (
    id              BIGSERIAL   PRIMARY KEY,
    layout_id       BIGINT      NOT NULL REFERENCES block_layouts (block_id) ON DELETE CASCADE,
    fixture_type    VARCHAR(20) NOT NULL,
    edge            VARCHAR(10) NOT NULL,
    position_offset INTEGER,
    direction       VARCHAR(10),
    label           VARCHAR(40),
    CONSTRAINT ck_layout_fixtures_type CHECK (fixture_type IN ('AC')),
    CONSTRAINT ck_layout_fixtures_edge CHECK (edge IN ('NORTH', 'SOUTH', 'EAST', 'WEST', 'CENTER')),
    CONSTRAINT ck_layout_fixtures_offset CHECK (position_offset IS NULL
        OR (edge <> 'CENTER' AND position_offset BETWEEN 1 AND 60)),
    CONSTRAINT ck_layout_fixtures_direction CHECK (direction IS NULL
        OR direction IN ('NORTH', 'SOUTH', 'EAST', 'WEST', 'CENTER'))
);

CREATE INDEX idx_layout_fixtures_layout ON layout_fixtures (layout_id);
