package in.r.cab.admin.controller;

import com.google.gson.Gson;
import in.r.cab.admin.model.Cab.State;
import in.r.cab.admin.model.request.AddCabRequest;
import in.r.cab.admin.model.request.UpdateCabRequest;
import in.r.cab.admin.service.CabsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CabController {

  private final CabsService cabsService;

  @Autowired
  public CabController(CabsService cabsService) {
    this.cabsService = cabsService;
  }

  @PostMapping(
      value = "cabs",
      headers = "Accept=application/json",
      produces = MediaType.APPLICATION_JSON_VALUE)
  @ResponseBody
  public void cabs(@RequestBody AddCabRequest request) {
    request.validate();
    cabsService.addCab(request.getDriverId(), request.getCityId(), request.getModel());
  }

  @PostMapping(
      value = "cabs/{cabId}",
      headers = "Accept=application/json",
      produces = MediaType.APPLICATION_JSON_VALUE)
  @ResponseBody
  public void updateCab(
      @PathVariable("cabId") Integer cabId, @RequestBody UpdateCabRequest request) {
    request.validate();
    cabsService.updateCab(cabId, request.getCityId(), State.valueOf(request.getState()));
  }

  @GetMapping(
      value = "cabs",
      headers = "Accept=application/json",
      produces = MediaType.APPLICATION_JSON_VALUE)
  @ResponseBody
  public String getAllCabs() {
    return new Gson().toJson(cabsService.getAllCabs());
  }
}
