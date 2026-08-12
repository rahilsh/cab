package in.rsh.cab.pricing.internal.web;

import in.rsh.cab.pricing.PricingService;
import in.rsh.cab.pricing.ProductStatus;
import in.rsh.cab.pricing.ServiceProduct;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

  private final PricingService pricing;

  public ProductController(PricingService pricing) {
    this.pricing = pricing;
  }

  @PostMapping
  public ResponseEntity<ServiceProduct> create(@Valid @RequestBody CreateProductRequest request) {
    ServiceProduct product = pricing.createProduct(
        request.slug(), request.name(), request.status(), request.capacity(), request.serviceClass());
    return ResponseEntity.created(URI.create("/api/v1/products")).body(product);
  }

  @GetMapping
  public List<ServiceProduct> list() {
    return pricing.listProducts();
  }

  public record CreateProductRequest(
      @NotBlank @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$") @Size(max = 63) String slug,
      @NotBlank @Size(max = 120) String name,
      @NotNull ProductStatus status,
      @Min(1) @Max(20) int capacity,
      @NotBlank @Size(max = 32) String serviceClass) {}
}
