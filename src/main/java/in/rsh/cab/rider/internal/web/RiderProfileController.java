package in.rsh.cab.rider.internal.web;

import in.rsh.cab.rider.RiderProfile;
import in.rsh.cab.rider.RiderProfileService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/rider/profile")
public class RiderProfileController {

  private final RiderProfileService profiles;

  public RiderProfileController(RiderProfileService profiles) {
    this.profiles = profiles;
  }

  @PostMapping
  public ResponseEntity<RiderProfile> create(@Valid @RequestBody ProfileRequest request) {
    RiderProfile profile = profiles.create(request.displayName(), request.phoneNumber());
    return ResponseEntity.created(URI.create("/api/v1/rider/profile")).body(profile);
  }

  @GetMapping
  public RiderProfile getOwn() {
    return profiles.getOwn();
  }

  @PutMapping
  public RiderProfile update(@Valid @RequestBody ProfileRequest request) {
    return profiles.updateOwn(request.displayName(), request.phoneNumber());
  }

  public record ProfileRequest(
      @NotBlank @Size(max = 120) String displayName,
      @Pattern(regexp = "^\\+?[0-9 -]{7,32}$") String phoneNumber) {}
}
