-- Do not invent medicine prices, costs, or stock for new catalog rows.
-- Existing values are preserved for operator review.
ALTER TABLE medication_catalog
    ALTER COLUMN unit_price SET DEFAULT 0,
    ALTER COLUMN cost_price SET DEFAULT 0,
    ALTER COLUMN stock_quantity SET DEFAULT 0,
    ALTER COLUMN min_stock_threshold SET DEFAULT 0;
