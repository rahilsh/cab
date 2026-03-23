package in.rsh.cab.model.response;

import java.time.LocalDateTime;

public record BookingResponse(
    Integer bookingId,
    LocalDateTime startTime,
    LocalDateTime endTime,
    String riderId,
    int cabId,
    String status,
    LocationResponse startLocation,
    LocationResponse endLocation,
    String bookedBy) {}
