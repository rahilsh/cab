package in.rsh.cab.controller;

import static in.rsh.cab.model.Cab.CabStatus;

import in.rsh.cab.model.request.AddCabRequest;
import in.rsh.cab.model.request.UpdateCabRequest;
import in.rsh.cab.model.response.CabResponse;
import in.rsh.cab.service.CabService;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

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
      @PathVariable("cabId") int cabId, @RequestBody UpdateCabRequest request) {
    request.validate();
    cabService.updateCab(cabId, request.cityId(), CabStatus.valueOf(request.state()));
  }

  @GetMapping(
      value = "cabs",
      headers = "Accept=application/json",
      produces = MediaType.APPLICATION_JSON_VALUE)
  @ResponseBody
  public ResponseEntity<List<CabResponse>> getAllCabs() {
    return ResponseEntity.ok(cabService.getAllCabsResponse());
  }
}
