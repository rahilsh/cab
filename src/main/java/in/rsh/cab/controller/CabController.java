package in.rsh.cab.controller;

import static in.rsh.cab.model.Cab.CabStatus;

import in.rsh.cab.model.request.AddCabRequest;
import in.rsh.cab.model.request.UpdateCabRequest;
import in.rsh.cab.model.response.CabResponse;
import in.rsh.cab.service.CabService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CabController {

  private static final int DEFAULT_PAGE = 0;
  private static final int DEFAULT_SIZE = 20;
  private static final int MAX_SIZE = 100;

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
  public ResponseEntity<Page<CabResponse>> getAllCabs(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    int safeSize = Math.min(size > 0 ? size : DEFAULT_SIZE, MAX_SIZE);
    int safePage = page >= 0 ? page : DEFAULT_PAGE;
    Pageable pageable = PageRequest.of(safePage, safeSize);
    return ResponseEntity.ok(cabService.getAllCabs(pageable));
  }
}
