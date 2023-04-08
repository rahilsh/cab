package in.rsh.cab.admin.model.request;

public record BookCabRequest(Integer employeeId, Integer fromCity, Integer toCity) {

  public void validate() {
    if (employeeId == null || fromCity == null || toCity == null) {
      throw new IllegalArgumentException("Missing mandatory params");
    }
  }
}
