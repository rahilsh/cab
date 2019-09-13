package in.rahilsh.cab.admin.controller;

import com.google.gson.Gson;
import in.rahilsh.cab.admin.model.Cab.State;
import in.rahilsh.cab.admin.model.request.AddCabRequest;
import in.rahilsh.cab.admin.model.request.UpdateCabRequest;
import in.rahilsh.cab.admin.service.CabsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CabController {

  @Autowired
  private CabsService cabsService;

  @RequestMapping(
      value = "cabs",
      method = RequestMethod.POST,
      headers = "Accept=application/json",
      produces = MediaType.APPLICATION_JSON_VALUE)
  @ResponseBody
  public void cabs(@RequestBody AddCabRequest request) {
    request.validate();
    cabsService.addCab(request.getDriverId(), request.getCityId(), request.getModel());
  }

  @RequestMapping(
      value = "cabs/{cabId}",
      method = RequestMethod.POST,
      headers = "Accept=application/json",
      produces = MediaType.APPLICATION_JSON_VALUE)
  @ResponseBody
  public void updateCab(@PathVariable("cabId") Integer cabId,
      @RequestBody UpdateCabRequest request) {
    request.validate();
    cabsService.updateCab(cabId, request.getCityId(), State.valueOf(request.getState()));
  }

  @RequestMapping(
      value = "cabs",
      method = RequestMethod.GET,
      headers = "Accept=application/json",
      produces = MediaType.APPLICATION_JSON_VALUE)
  @ResponseBody
  public String getAllCabs() {
    return new Gson().toJson(cabsService.getAllCabs());
  }
}
