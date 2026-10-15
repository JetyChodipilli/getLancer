-- Retained history includes superseded and detached draft packages. Quota checks examine at most
-- one hundred owner rows while holding the existing publishing owner transaction advisory lock.
CREATE INDEX education_free_packages_owner ON education_free_packages(owner_id);
