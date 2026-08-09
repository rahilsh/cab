ALTER TABLE driver_offers DROP CONSTRAINT uq_driver_offer_ride_driver;

CREATE UNIQUE INDEX uq_driver_offer_attempt_driver
    ON driver_offers (tenant_id, attempt_id, driver_id);
