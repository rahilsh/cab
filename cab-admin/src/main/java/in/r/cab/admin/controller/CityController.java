package in.r.cab.admin.controller;

import com.google.gson.Gson;
import in.r.cab.admin.model.request.AddCityRequest;
import in.r.cab.admin.service.CityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CityController {

  @Autowired
  private CityService cityService;

  @RequestMapping(
      value = "cities",
      method = RequestMethod.POST,
      headers = "Accept=application/json",
      produces = MediaType.APPLICATION_JSON_VALUE)
  @ResponseBody
  public void cities(@RequestBody AddCityRequest request) {
    request.validate();
    cityService.addCity(request.getName());
  }

  @RequestMapping(
      value = "cities",
      method = RequestMethod.GET,
      headers = "Accept=application/json",
      produces = MediaType.APPLICATION_JSON_VALUE)
  @ResponseBody
  public String getAllCities() {
    return new Gson().toJson(cityService.getAllCities());
  }
}
