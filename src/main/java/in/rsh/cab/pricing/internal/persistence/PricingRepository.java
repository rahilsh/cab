package in.rsh.cab.pricing.internal.persistence;

import in.rsh.cab.pricing.FareQuote;
import in.rsh.cab.pricing.PricingRule;
import in.rsh.cab.pricing.ServiceProduct;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PricingRepository {

  void insertProduct(UUID tenantId, ServiceProduct product);

  List<ServiceProduct> findProducts(UUID tenantId);

  Optional<ServiceProduct> findProduct(UUID tenantId, UUID productId);

  int nextRuleVersion(UUID tenantId, UUID productId);

  void insertRule(UUID tenantId, PricingRule rule);

  List<PricingRule> findRules(UUID tenantId);

  Optional<PricingRule> findEffectiveRule(UUID tenantId, UUID productId, Instant at);

  void insertQuote(UUID tenantId, UUID riderAccountId, FareQuote quote);

  Optional<FareQuote> findQuote(UUID tenantId, UUID riderAccountId, UUID quoteId);

  List<FareQuote> findQuotes(UUID tenantId, UUID riderAccountId);
}
