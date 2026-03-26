package in.rsh.cab.model;

public record BookingRequest(
    String employeeId,
    Integer fromCity,
    Integer toCity,
    String idempotencyKey) {}
