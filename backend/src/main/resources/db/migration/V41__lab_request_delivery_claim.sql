-- Provider HTTP runs outside database transactions; a bounded claim serializes one run.
ALTER TABLE lab_runs ADD COLUMN request_delivery_command uuid;
ALTER TABLE lab_runs ADD COLUMN request_delivery_until timestamptz;
ALTER TABLE lab_runs ADD CONSTRAINT lab_request_delivery_pair CHECK ((request_delivery_command IS NULL) = (request_delivery_until IS NULL));
