package in.rsh.cab.rating.internal.web;

import in.rsh.cab.rating.Rating;
import in.rsh.cab.rating.RatingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class RatingController {

  private final RatingService ratings;

  public RatingController(RatingService ratings) {
    this.ratings = ratings;
  }

  @PostMapping("/rides/{rideId}/ratings")
  public ResponseEntity<Rating> create(
      @PathVariable UUID rideId, @Valid @RequestBody RatingRequest request) {
    Rating rating = ratings.create(rideId, request.score(), request.comment());
    return ResponseEntity.created(URI.create("/api/v1/ratings/" + rating.id())).body(rating);
  }

  @GetMapping("/ratings/{ratingId}")
  public Rating getOwn(@PathVariable UUID ratingId) {
    return ratings.getOwn(ratingId);
  }

  public record RatingRequest(@Min(1) @Max(5) int score, @Size(max = 1000) String comment) {}
}
