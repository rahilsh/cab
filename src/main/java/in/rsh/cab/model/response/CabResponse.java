package in.rsh.cab.model.response;

public record CabResponse(
    Integer cabId,
    String driverId,
    String cabNumber,
    String status,
    String type,
    LocationResponse location,
    Long idleFrom,
    Integer cityId,
    String model) {}
