package in.rsh.cab.pricing.internal.web;

import in.rsh.cab.pricing.PricingRule;
import in.rsh.cab.pricing.PricingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/pricing-rules")
public class PricingRuleController {

  private final PricingService pricing;

  public PricingRuleController(PricingService pricing) {
    this.pricing = pricing;
  }

  @PostMapping
  public ResponseEntity<PricingRule> create(@Valid @RequestBody CreatePricingRuleRequest request) {
    PricingRule rule = pricing.createRule(
        request.productId(), request.effectiveFrom(), request.effectiveTo(), request.baseFareMinor(),
        request.perKmMinor(), request.perMinuteMinor(), request.minimumFareMinor(), request.currency(),
        request.surgeBasisPoints(), request.taxBasisPoints(), request.active());
    return ResponseEntity.created(URI.create("/api/v1/pricing-rules")).body(rule);
  }

  @GetMapping
  public List<PricingRule> list() {
    return pricing.listRules();
  }

  public record CreatePricingRuleRequest(
      @NotNull UUID productId,
      @NotNull Instant effectiveFrom,
      Instant effectiveTo,
      @PositiveOrZero long baseFareMinor,
      @PositiveOrZero long perKmMinor,
      @PositiveOrZero long perMinuteMinor,
      @PositiveOrZero long minimumFareMinor,
      @NotBlank @Pattern(regexp = "^[A-Za-z]{3}$") String currency,
      @PositiveOrZero @Max(100000) Integer surgeBasisPoints,
      @PositiveOrZero @Max(10000) Integer taxBasisPoints,
      boolean active) {}
}
