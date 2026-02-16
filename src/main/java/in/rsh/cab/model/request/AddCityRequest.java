package in.rsh.cab.model.request;

public record AddCityRequest(String name, String state) {

  public void validate() {
    if (name == null) {
      throw new IllegalArgumentException("Missing param");
    }
  }
}
