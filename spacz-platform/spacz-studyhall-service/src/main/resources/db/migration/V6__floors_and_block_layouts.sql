-- Physical seating layout: study hall → floors → blocks → block layout → layout cells → seats.
-- Bookings keep referencing seats. Layouts hold no booking state.
-- Existing data: each hall with blocks gets a "Ground Floor" holding them; each block gets a GRID layout
-- of its size with one SEAT cell per existing seat. Gaps stay implicit (no cell = EMPTY).
-- blocks.study_hall_id and seats.row_index / column_index are kept (hall-scoped queries, existing APIs);
-- composite foreign keys keep them consistent with the floor and the layout cell.

-- 1. Floors
CREATE TABLE floors (
    id            BIGSERIAL    PRIMARY KEY,
    study_hall_id BIGINT       NOT NULL REFERENCES study_halls (id) ON DELETE CASCADE,
    floor_number  INTEGER      NOT NULL,
    name          VARCHAR(80)  NOT NULL,
    description   VARCHAR(500),
    display_order INTEGER      NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_floors_hall_number UNIQUE (study_hall_id, floor_number),
    CONSTRAINT uk_floors_id_hall UNIQUE (id, study_hall_id),
    CONSTRAINT ck_floors_number CHECK (floor_number BETWEEN -5 AND 200)
);

INSERT INTO floors (study_hall_id, floor_number, name, display_order)
SELECT DISTINCT study_hall_id, 0, 'Ground Floor', 0 FROM blocks;

-- 2. Blocks belong to a floor of their own hall (deferred: JPA may write the floor and block in any order).
ALTER TABLE blocks ADD COLUMN floor_id BIGINT;
UPDATE blocks b SET floor_id = f.id FROM floors f WHERE f.study_hall_id = b.study_hall_id;
ALTER TABLE blocks ALTER COLUMN floor_id SET NOT NULL;
ALTER TABLE blocks ADD CONSTRAINT fk_blocks_floor FOREIGN KEY (floor_id, study_hall_id)
    REFERENCES floors (id, study_hall_id) DEFERRABLE INITIALLY DEFERRED;
CREATE INDEX idx_blocks_floor ON blocks (floor_id);

-- Grids always contained their seats; make sure before copying the size into the layouts.
UPDATE blocks b
SET total_rows = GREATEST(b.total_rows, s.max_row), total_columns = GREATEST(b.total_columns, s.max_column)
FROM (SELECT block_id, max(row_index) AS max_row, max(column_index) AS max_column FROM seats GROUP BY block_id) s
WHERE s.block_id = b.id AND (s.max_row > b.total_rows OR s.max_column > b.total_columns);

-- 3. One layout per block, sharing the block's id.
CREATE TABLE block_layouts (
    block_id       BIGINT      PRIMARY KEY REFERENCES blocks (id) ON DELETE CASCADE,
    layout_type    VARCHAR(20) NOT NULL,
    row_count      INTEGER     NOT NULL,
    column_count   INTEGER     NOT NULL,
    layout_version BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT ck_block_layouts_type CHECK (layout_type IN ('GRID', 'CUSTOM')),
    CONSTRAINT ck_block_layouts_size CHECK (row_count BETWEEN 1 AND 60 AND column_count BETWEEN 1 AND 60)
);

INSERT INTO block_layouts (block_id, layout_type, row_count, column_count)
SELECT id, 'GRID', total_rows, total_columns FROM blocks;

-- 4. Layout cells: occupied positions only. Exactly SEAT cells reference a seat, each seat at most once.
CREATE TABLE layout_cells (
    id           BIGSERIAL   PRIMARY KEY,
    layout_id    BIGINT      NOT NULL REFERENCES block_layouts (block_id) ON DELETE CASCADE,
    row_index    INTEGER     NOT NULL,
    column_index INTEGER     NOT NULL,
    cell_type    VARCHAR(20) NOT NULL,
    seat_id      BIGINT,
    label        VARCHAR(40),
    CONSTRAINT uk_layout_cells_position UNIQUE (layout_id, row_index, column_index),
    CONSTRAINT uk_layout_cells_seat UNIQUE (seat_id),
    CONSTRAINT ck_layout_cells_position CHECK (row_index BETWEEN 1 AND 60 AND column_index BETWEEN 1 AND 60),
    CONSTRAINT ck_layout_cells_type CHECK (cell_type IN ('EMPTY', 'SEAT', 'WALKWAY', 'ENTRANCE', 'EXIT', 'TABLE',
        'WALL', 'PILLAR', 'RECEPTION', 'STAIRS', 'LIFT', 'RESTROOM', 'OTHER')),
    CONSTRAINT ck_layout_cells_seat CHECK ((cell_type = 'SEAT') = (seat_id IS NOT NULL))
);

-- A SEAT cell's seat belongs to the layout's block and sits at the cell's position. Deleting the seat
-- deletes its cell. Deferred: a seat moved to another block changes its cell in the same transaction.
ALTER TABLE seats ADD CONSTRAINT uk_seats_block_cell UNIQUE (id, block_id, row_index, column_index);
ALTER TABLE layout_cells ADD CONSTRAINT fk_layout_cells_seat FOREIGN KEY (seat_id, layout_id, row_index, column_index)
    REFERENCES seats (id, block_id, row_index, column_index) ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED;

INSERT INTO layout_cells (layout_id, row_index, column_index, cell_type, seat_id)
SELECT block_id, row_index, column_index, 'SEAT', id FROM seats;
