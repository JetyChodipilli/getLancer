ALTER TABLE products ADD COLUMN pricing_mode varchar(20) NOT NULL DEFAULT 'NONE';
ALTER TABLE products ADD COLUMN price_min_minor bigint;
ALTER TABLE products ADD COLUMN price_max_minor bigint;
ALTER TABLE products ADD COLUMN currency_code char(3);
ALTER TABLE products ADD CONSTRAINT showcase_pricing_bounds CHECK (
 (pricing_mode IN ('NONE','CUSTOM_QUOTE') AND price_min_minor IS NULL AND price_max_minor IS NULL AND currency_code IS NULL)
 OR (pricing_mode='STARTING_FROM' AND price_min_minor IS NOT NULL AND price_min_minor BETWEEN 0 AND 100000000000 AND price_max_minor IS NULL AND currency_code IS NOT NULL AND currency_code ~ '^[A-Z]{3}$')
 OR (pricing_mode='RANGE' AND price_min_minor IS NOT NULL AND price_max_minor IS NOT NULL AND price_min_minor BETWEEN 0 AND 100000000000 AND price_max_minor BETWEEN price_min_minor AND 100000000000 AND currency_code IS NOT NULL AND currency_code ~ '^[A-Z]{3}$')
);
