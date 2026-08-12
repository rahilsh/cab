package in.rsh.cab.tenancy.internal.web;

import in.rsh.cab.tenancy.TenantInvitation;
import in.rsh.cab.tenancy.TenantMembership;
import in.rsh.cab.tenancy.TenantRole;
import in.rsh.cab.tenancy.TenantService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TenantInvitationController {

  private final TenantService tenants;

  public TenantInvitationController(TenantService tenants) {
    this.tenants = tenants;
  }

  @PostMapping("/api/v1/current-tenant/invitations")
  public ResponseEntity<TenantInvitation> invite(@Valid @RequestBody InviteRequest request) {
    TenantInvitation invitation = tenants.invite(request.email(), request.roles());
    return ResponseEntity.status(201).body(invitation);
  }

  @DeleteMapping("/api/v1/current-tenant/invitations/{id}")
  public ResponseEntity<Void> revoke(@PathVariable UUID id) {
    tenants.revokeInvitation(id);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/api/v1/current-tenant/memberships")
  public ResponseEntity<TenantMembership> addExisting(
      @Valid @RequestBody AddMembershipRequest request) {
    TenantMembership membership = tenants.addExistingAccount(request.accountId(), request.roles());
    return ResponseEntity.status(201).body(membership);
  }

  @PostMapping("/api/v1/tenant-invitations/accept")
  public TenantMembership accept(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AcceptInvitationRequest request) {
    return tenants.acceptInvitation(
        request.token(),
        jwt.getIssuer().toString(),
        jwt.getSubject(),
        jwt.getClaimAsString("email"),
        jwt.getClaimAsString("name"),
        Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified")));
  }

  public record InviteRequest(
      @NotBlank @Email @Size(max = 320) String email,
      @NotEmpty Set<@NotNull TenantRole> roles) {}

  public record AddMembershipRequest(
      @NotNull UUID accountId, @NotEmpty Set<@NotNull TenantRole> roles) {}

  public record AcceptInvitationRequest(@NotBlank @Size(max = 256) String token) {}
}
