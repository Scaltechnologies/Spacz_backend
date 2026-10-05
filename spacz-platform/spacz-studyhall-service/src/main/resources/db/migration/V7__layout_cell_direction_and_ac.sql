-- Physical orientation of layout cells and AC fixtures.
-- Row 1 = row A is the entrance/user side of a block; higher rows are further away (unchanged data).
-- direction: the way a seat faces, an entrance/exit opens, an AC blows (CENTER = centralized AC) or
-- stairs/lift lead. NULL = unspecified (all existing cells). Only directional types may have one.

ALTER TABLE layout_cells ADD COLUMN direction VARCHAR(10);

ALTER TABLE layout_cells DROP CONSTRAINT ck_layout_cells_type;
ALTER TABLE layout_cells ADD CONSTRAINT ck_layout_cells_type CHECK (cell_type IN ('EMPTY', 'SEAT', 'WALKWAY', 'ENTRANCE',
    'EXIT', 'TABLE', 'WALL', 'PILLAR', 'RECEPTION', 'STAIRS', 'LIFT', 'RESTROOM', 'AC', 'OTHER'));

ALTER TABLE layout_cells ADD CONSTRAINT ck_layout_cells_direction CHECK (
    direction IS NULL
    OR (direction IN ('UP', 'DOWN', 'LEFT', 'RIGHT')
        AND cell_type IN ('SEAT', 'ENTRANCE', 'EXIT', 'AC', 'STAIRS', 'LIFT'))
    OR (direction = 'CENTER' AND cell_type = 'AC'));
