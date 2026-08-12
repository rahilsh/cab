ALTER TABLE support_cases ADD COLUMN version bigint NOT NULL DEFAULT 0;
ALTER TABLE safety_incidents ADD COLUMN version bigint NOT NULL DEFAULT 0;

ALTER TABLE rides ADD CONSTRAINT chk_ride_status_timestamps_v17 CHECK (
    (assigned_at IS NULL OR assigned_at >= requested_at)
    AND (arrived_at IS NULL OR (assigned_at IS NOT NULL AND arrived_at >= assigned_at))
    AND (started_at IS NULL OR (arrived_at IS NOT NULL AND started_at >= arrived_at))
    AND (completed_at IS NULL OR (started_at IS NOT NULL AND completed_at >= started_at))
    AND (cancelled_at IS NULL OR cancelled_at >= requested_at)
    AND (status NOT IN ('DRIVER_ASSIGNED', 'DRIVER_ARRIVING', 'DRIVER_ARRIVED',
                        'IN_PROGRESS', 'COMPLETED') OR assigned_at IS NOT NULL)
    AND (status NOT IN ('DRIVER_ARRIVED', 'IN_PROGRESS', 'COMPLETED') OR arrived_at IS NOT NULL)
    AND (status NOT IN ('IN_PROGRESS', 'COMPLETED') OR started_at IS NOT NULL)
    AND ((status = 'COMPLETED') = (completed_at IS NOT NULL))
) NOT VALID;

ALTER TABLE rides ADD CONSTRAINT chk_ride_status_assignment_v17 CHECK (
    (status NOT IN ('DRIVER_ASSIGNED', 'DRIVER_ARRIVING', 'DRIVER_ARRIVED',
                    'IN_PROGRESS', 'COMPLETED')
     OR (driver_id IS NOT NULL AND vehicle_id IS NOT NULL AND driver_shift_id IS NOT NULL))
    AND (status NOT IN ('REQUESTED', 'MATCHING', 'NO_DRIVER')
     OR (driver_id IS NULL AND vehicle_id IS NULL AND driver_shift_id IS NULL))
) NOT VALID;

ALTER TABLE rides VALIDATE CONSTRAINT chk_ride_status_timestamps_v17;
ALTER TABLE rides VALIDATE CONSTRAINT chk_ride_status_assignment_v17;

CREATE INDEX idx_driver_location_created_at_v17
    ON driver_location_checkpoints (tenant_id, created_at, id);
