package in.rsh.cab.pricing.internal.web;

import in.rsh.cab.geography.GeoPoint;
import in.rsh.cab.pricing.FareQuote;
import in.rsh.cab.pricing.PricingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/quotes")
public class FareQuoteController {

  private final PricingService pricing;

  public FareQuoteController(PricingService pricing) {
    this.pricing = pricing;
  }

  @PostMapping
  public ResponseEntity<FareQuote> create(
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @Valid @RequestBody CreateFareQuoteRequest request) {
    PricingService.QuoteCreation result = pricing.createQuote(
        idempotencyKey, request.productId(), request.pickup().toGeoPoint(), request.dropoff().toGeoPoint());
    return ResponseEntity.status(result.httpStatus())
        .location(URI.create("/api/v1/quotes/" + result.quote().id()))
        .body(result.quote());
  }

  @GetMapping("/{id}")
  public FareQuote get(@PathVariable UUID id) {
    return pricing.getOwnQuote(id);
  }

  @GetMapping
  public List<FareQuote> list() {
    return pricing.listOwnQuotes();
  }

  public record CreateFareQuoteRequest(
      @NotNull UUID productId, @NotNull @Valid PointRequest pickup, @NotNull @Valid PointRequest dropoff) {}

  public record PointRequest(
      @NotNull @DecimalMin("-90") @DecimalMax("90") Double latitude,
      @NotNull @DecimalMin("-180") @DecimalMax("180") Double longitude) {

    GeoPoint toGeoPoint() {
      return new GeoPoint(latitude, longitude);
    }
  }
}
