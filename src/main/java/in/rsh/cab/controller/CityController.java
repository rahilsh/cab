package in.rsh.cab.controller;

import com.google.gson.Gson;
import in.rsh.cab.model.request.AddCityRequest;
import in.rsh.cab.service.CityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CityController {

  private final CityService cityService;

  @Autowired
  public CityController(CityService cityService) {
    this.cityService = cityService;
  }

  @PostMapping(
      value = "cities",
      headers = "Accept=application/json",
      produces = MediaType.APPLICATION_JSON_VALUE)
  @ResponseBody
  public void addCity(@RequestBody AddCityRequest request) {
    request.validate();
    cityService.addCity(request.name());
  }

  @GetMapping(
      value = "cities",
      headers = "Accept=application/json",
      produces = MediaType.APPLICATION_JSON_VALUE)
  @ResponseBody
  public String getAllCities() {
    return new Gson().toJson(cityService.getAllCities());
  }
}
