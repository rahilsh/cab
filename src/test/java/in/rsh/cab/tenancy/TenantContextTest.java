package in.rsh.cab.tenancy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TenantContextTest {

  @AfterEach
  void clear() {
    TenantContext.clear();
  }

  @Test
  void storesImmutableRequestContextAndRequiresSelection() {
    Set<TenantRole> roles = new HashSet<>(Set.of(TenantRole.RIDER));
    TenantContext context =
        new TenantContext(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), roles);
    roles.clear();
    TenantContext.set(context);

    assertEquals(Set.of(TenantRole.RIDER), TenantContext.require().roles());
    TenantContext.clear();
    assertThrows(IllegalStateException.class, TenantContext::require);
  }
}
