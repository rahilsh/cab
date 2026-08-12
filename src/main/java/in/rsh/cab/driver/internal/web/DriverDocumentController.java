package in.rsh.cab.driver.internal.web;

import in.rsh.cab.driver.DriverDocument;
import in.rsh.cab.driver.DriverDocumentService;
import in.rsh.cab.driver.DriverDocumentType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class DriverDocumentController {

  private final DriverDocumentService documents;

  public DriverDocumentController(DriverDocumentService documents) {
    this.documents = documents;
  }

  @PostMapping("/drivers/me/documents")
  public ResponseEntity<DriverDocument> submit(@Valid @RequestBody SubmitRequest request) {
    DriverDocument document = documents.submit(request.documentType(), request.documentReference(),
        request.objectKey(), request.expiresOn());
    return ResponseEntity.created(URI.create("/api/v1/drivers/me/documents/" + document.id()))
        .body(document);
  }

  @GetMapping("/drivers/me/documents")
  public List<DriverDocument> listOwn() {
    return documents.listOwn();
  }

  @GetMapping("/drivers/me/documents/{documentId}")
  public DriverDocument getOwn(@PathVariable UUID documentId) {
    return documents.getOwn(documentId);
  }

  @GetMapping("/drivers/{driverId}/documents")
  public List<DriverDocument> list(@PathVariable UUID driverId) {
    return documents.list(driverId);
  }

  @PostMapping("/drivers/{driverId}/documents/{documentId}/verify")
  public DriverDocument verify(@PathVariable UUID driverId, @PathVariable UUID documentId) {
    return documents.verify(driverId, documentId);
  }

  @PostMapping("/drivers/{driverId}/documents/{documentId}/reject")
  public DriverDocument reject(
      @PathVariable UUID driverId, @PathVariable UUID documentId,
      @Valid @RequestBody RejectRequest request) {
    return documents.reject(driverId, documentId, request.reason());
  }

  public record SubmitRequest(
      @NotNull DriverDocumentType documentType,
      @NotBlank @Size(max = 255) String documentReference,
      @NotBlank @Size(max = 512) String objectKey,
      LocalDate expiresOn) {}

  public record RejectRequest(@NotBlank @Size(max = 500) String reason) {}
}
