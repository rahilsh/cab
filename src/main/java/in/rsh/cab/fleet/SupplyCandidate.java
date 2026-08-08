package in.rsh.cab.fleet;

import java.util.UUID;

public record SupplyCandidate(UUID shiftId, UUID driverId, UUID vehicleId) {}
