package in.rsh.cab.admin.controller;

import com.google.gson.Gson;
import in.rsh.cab.admin.model.request.AddCabRequest;
import in.rsh.cab.admin.model.request.UpdateCabRequest;
import in.rsh.cab.admin.service.CabService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import static in.rsh.cab.commons.model.Cab.CabStatus;

@RestController
public class CabController {

  private final CabService cabService;

  @Autowired
  public CabController(CabService cabService) {
    this.cabService = cabService;
  }

  @PostMapping(
      value = "cabs",
      headers = "Accept=application/json",
      produces = MediaType.APPLICATION_JSON_VALUE)
  @ResponseBody
  public void addCab(@RequestBody AddCabRequest request) {
    request.validate();
    cabService.addCab(request.driverId(), request.cityId(), request.model());
  }

  @PostMapping(
      value = "cabs/{cabId}",
      headers = "Accept=application/json",
      produces = MediaType.APPLICATION_JSON_VALUE)
  @ResponseBody
  public void updateCab(
      @PathVariable("cabId") Integer cabId, @RequestBody UpdateCabRequest request) {
    request.validate();
    cabService.updateCab(cabId, request.cityId(), CabStatus.valueOf(request.state()));
  }

  @GetMapping(
      value = "cabs",
      headers = "Accept=application/json",
      produces = MediaType.APPLICATION_JSON_VALUE)
  @ResponseBody
  public String getAllCabs() {
    return new Gson().toJson(cabService.getAllCabs());
  }
}
