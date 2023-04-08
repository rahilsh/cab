package in.rsh.cab.admin.model;

public record Booking(Integer id, Integer cabId, Integer employeeId, Integer fromCity,
                      Integer toCity, Long startTime) {

}
