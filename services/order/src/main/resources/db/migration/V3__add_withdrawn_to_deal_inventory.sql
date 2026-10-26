-- Set when the catalog withdraws a deal; placements are rejected from then on.
ALTER TABLE deal_inventory ADD COLUMN withdrawn BOOLEAN NOT NULL DEFAULT false;
