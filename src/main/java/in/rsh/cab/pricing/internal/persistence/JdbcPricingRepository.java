package in.rsh.cab.pricing.internal.persistence;

import in.rsh.cab.geography.GeoPoint;
import in.rsh.cab.pricing.FareQuote;
import in.rsh.cab.pricing.PricingRule;
import in.rsh.cab.pricing.ProductStatus;
import in.rsh.cab.pricing.QuoteStatus;
import in.rsh.cab.pricing.ServiceProduct;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcPricingRepository implements PricingRepository {

  private static final String PRODUCT_SELECT = """
      SELECT id, slug, name, status, capacity, service_class, created_at, updated_at
      FROM service_products
      """;
  private static final String RULE_SELECT = """
      SELECT id, product_id, version, effective_from, effective_to, base_fare_minor,
             per_km_minor, per_minute_minor, minimum_fare_minor, currency,
             surge_basis_points, tax_basis_points, active, created_at, updated_at
      FROM pricing_rules
      """;
  private static final String QUOTE_SELECT = """
      SELECT id, product_id, pricing_rule_id, pricing_rule_version,
             ST_Y(pickup) AS pickup_latitude, ST_X(pickup) AS pickup_longitude,
             ST_Y(dropoff) AS dropoff_latitude, ST_X(dropoff) AS dropoff_longitude,
             route_distance_meters, route_duration_seconds, base_rate_minor,
             per_km_rate_minor, per_minute_rate_minor, minimum_fare_minor,
             surge_basis_points, tax_basis_points, base_fare_minor, distance_fare_minor,
             time_fare_minor, minimum_adjustment_minor, subtotal_minor, surge_minor,
             tax_minor, total_minor, currency, status, expires_at, request_fingerprint,
             created_at, updated_at, version
      FROM fare_quotes
      """;

  private final JdbcClient jdbc;

  public JdbcPricingRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void insertProduct(UUID tenantId, ServiceProduct product) {
    jdbc.sql("""
            INSERT INTO service_products
              (id, tenant_id, slug, name, status, capacity, service_class, created_at, updated_at)
            VALUES
              (:id, :tenantId, :slug, :name, :status, :capacity, :serviceClass, :createdAt, :updatedAt)
            """)
        .param("id", product.id()).param("tenantId", tenantId).param("slug", product.slug())
        .param("name", product.name()).param("status", product.status().name())
        .param("capacity", product.capacity()).param("serviceClass", product.serviceClass())
        .param("createdAt", Timestamp.from(product.createdAt()))
        .param("updatedAt", Timestamp.from(product.updatedAt())).update();
  }

  @Override
  public List<ServiceProduct> findProducts(UUID tenantId) {
    return jdbc.sql(PRODUCT_SELECT + " WHERE tenant_id = :tenantId ORDER BY name, id")
        .param("tenantId", tenantId).query(this::mapProduct).list();
  }

  @Override
  public Optional<ServiceProduct> findProduct(UUID tenantId, UUID productId) {
    return jdbc.sql(PRODUCT_SELECT + " WHERE tenant_id = :tenantId AND id = :productId")
        .param("tenantId", tenantId).param("productId", productId).query(this::mapProduct).optional();
  }

  @Override
  public int nextRuleVersion(UUID tenantId, UUID productId) {
    return jdbc.sql("""
            SELECT COALESCE(MAX(version), 0) + 1 FROM pricing_rules
            WHERE tenant_id = :tenantId AND product_id = :productId
            """)
        .param("tenantId", tenantId).param("productId", productId).query(Integer.class).single();
  }

  @Override
  public void insertRule(UUID tenantId, PricingRule rule) {
    jdbc.sql("""
            INSERT INTO pricing_rules
              (id, tenant_id, product_id, version, effective_from, effective_to,
               base_fare_minor, per_km_minor, per_minute_minor, minimum_fare_minor,
               currency, surge_basis_points, tax_basis_points, active, created_at, updated_at)
            VALUES
              (:id, :tenantId, :productId, :version, :effectiveFrom, :effectiveTo,
               :baseFare, :perKm, :perMinute, :minimumFare, :currency, :surge, :tax,
               :active, :createdAt, :updatedAt)
            """)
        .param("id", rule.id()).param("tenantId", tenantId).param("productId", rule.productId())
        .param("version", rule.version()).param("effectiveFrom", Timestamp.from(rule.effectiveFrom()))
        .param("effectiveTo", timestamp(rule.effectiveTo())).param("baseFare", rule.baseFareMinor())
        .param("perKm", rule.perKmMinor()).param("perMinute", rule.perMinuteMinor())
        .param("minimumFare", rule.minimumFareMinor()).param("currency", rule.currency())
        .param("surge", rule.surgeBasisPoints()).param("tax", rule.taxBasisPoints())
        .param("active", rule.active()).param("createdAt", Timestamp.from(rule.createdAt()))
        .param("updatedAt", Timestamp.from(rule.updatedAt())).update();
  }

  @Override
  public List<PricingRule> findRules(UUID tenantId) {
    return jdbc.sql(RULE_SELECT + " WHERE tenant_id = :tenantId ORDER BY product_id, version DESC")
        .param("tenantId", tenantId).query(this::mapRule).list();
  }

  @Override
  public Optional<PricingRule> findEffectiveRule(UUID tenantId, UUID productId, Instant at) {
    return jdbc.sql(RULE_SELECT + """
            WHERE tenant_id = :tenantId AND product_id = :productId AND active
              AND effective_from <= :at AND (effective_to IS NULL OR effective_to > :at)
            ORDER BY version DESC LIMIT 1
            """)
        .param("tenantId", tenantId).param("productId", productId)
        .param("at", Timestamp.from(at)).query(this::mapRule).optional();
  }

  @Override
  public void insertQuote(UUID tenantId, UUID riderAccountId, FareQuote quote) {
    jdbc.sql("""
            INSERT INTO fare_quotes
              (id, tenant_id, rider_account_id, product_id, pricing_rule_id, pricing_rule_version,
               pickup, dropoff, route_distance_meters, route_duration_seconds, base_rate_minor,
               per_km_rate_minor, per_minute_rate_minor, minimum_fare_minor, surge_basis_points,
               tax_basis_points, base_fare_minor, distance_fare_minor, time_fare_minor,
               minimum_adjustment_minor, subtotal_minor, surge_minor, tax_minor, total_minor,
               currency, status, expires_at, request_fingerprint, created_at, updated_at, version)
            VALUES
              (:id, :tenantId, :riderAccountId, :productId, :ruleId, :ruleVersion,
               ST_SetSRID(ST_MakePoint(:pickupLongitude, :pickupLatitude), 4326),
               ST_SetSRID(ST_MakePoint(:dropoffLongitude, :dropoffLatitude), 4326),
               :distance, :duration, :baseRate, :perKmRate, :perMinuteRate, :minimumFare,
               :surgeBps, :taxBps, :baseFare, :distanceFare, :timeFare, :minimumAdjustment,
               :subtotal, :surge, :tax, :total, :currency, :status, :expiresAt,
               :fingerprint, :createdAt, :updatedAt, :version)
            """)
        .param("id", quote.id()).param("tenantId", tenantId).param("riderAccountId", riderAccountId)
        .param("productId", quote.productId()).param("ruleId", quote.pricingRuleId())
        .param("ruleVersion", quote.pricingRuleVersion())
        .param("pickupLongitude", quote.pickup().longitude()).param("pickupLatitude", quote.pickup().latitude())
        .param("dropoffLongitude", quote.dropoff().longitude()).param("dropoffLatitude", quote.dropoff().latitude())
        .param("distance", quote.routeDistanceMeters()).param("duration", quote.routeDurationSeconds())
        .param("baseRate", quote.baseRateMinor()).param("perKmRate", quote.perKmRateMinor())
        .param("perMinuteRate", quote.perMinuteRateMinor()).param("minimumFare", quote.minimumFareMinor())
        .param("surgeBps", quote.surgeBasisPoints()).param("taxBps", quote.taxBasisPoints())
        .param("baseFare", quote.baseFareMinor()).param("distanceFare", quote.distanceFareMinor())
        .param("timeFare", quote.timeFareMinor()).param("minimumAdjustment", quote.minimumAdjustmentMinor())
        .param("subtotal", quote.subtotalMinor()).param("surge", quote.surgeMinor())
        .param("tax", quote.taxMinor()).param("total", quote.totalMinor()).param("currency", quote.currency())
        .param("status", quote.status().name()).param("expiresAt", Timestamp.from(quote.expiresAt()))
        .param("fingerprint", quote.requestFingerprint()).param("createdAt", Timestamp.from(quote.createdAt()))
        .param("updatedAt", Timestamp.from(quote.updatedAt())).param("version", quote.version()).update();
  }

  @Override
  public Optional<FareQuote> findQuote(UUID tenantId, UUID riderAccountId, UUID quoteId) {
    return jdbc.sql(QUOTE_SELECT + """
            WHERE tenant_id = :tenantId AND rider_account_id = :riderAccountId AND id = :quoteId
            """)
        .param("tenantId", tenantId).param("riderAccountId", riderAccountId).param("quoteId", quoteId)
        .query(this::mapQuote).optional();
  }

  @Override
  public List<FareQuote> findQuotes(UUID tenantId, UUID riderAccountId) {
    return jdbc.sql(QUOTE_SELECT + """
            WHERE tenant_id = :tenantId AND rider_account_id = :riderAccountId
            ORDER BY created_at DESC, id
            """)
        .param("tenantId", tenantId).param("riderAccountId", riderAccountId)
        .query(this::mapQuote).list();
  }

  private ServiceProduct mapProduct(ResultSet rs, int rowNumber) throws SQLException {
    return new ServiceProduct(rs.getObject("id", UUID.class), rs.getString("slug"), rs.getString("name"),
        ProductStatus.valueOf(rs.getString("status")), rs.getInt("capacity"), rs.getString("service_class"),
        rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());
  }

  private PricingRule mapRule(ResultSet rs, int rowNumber) throws SQLException {
    return new PricingRule(rs.getObject("id", UUID.class), rs.getObject("product_id", UUID.class),
        rs.getInt("version"), rs.getTimestamp("effective_from").toInstant(), instant(rs, "effective_to"),
        rs.getLong("base_fare_minor"), rs.getLong("per_km_minor"), rs.getLong("per_minute_minor"),
        rs.getLong("minimum_fare_minor"), rs.getString("currency"), integer(rs, "surge_basis_points"),
        integer(rs, "tax_basis_points"), rs.getBoolean("active"), rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant());
  }

  private FareQuote mapQuote(ResultSet rs, int rowNumber) throws SQLException {
    return new FareQuote(rs.getObject("id", UUID.class), rs.getObject("product_id", UUID.class),
        rs.getObject("pricing_rule_id", UUID.class), rs.getInt("pricing_rule_version"),
        new GeoPoint(rs.getDouble("pickup_latitude"), rs.getDouble("pickup_longitude")),
        new GeoPoint(rs.getDouble("dropoff_latitude"), rs.getDouble("dropoff_longitude")),
        rs.getLong("route_distance_meters"), rs.getLong("route_duration_seconds"),
        rs.getLong("base_rate_minor"), rs.getLong("per_km_rate_minor"), rs.getLong("per_minute_rate_minor"),
        rs.getLong("minimum_fare_minor"), rs.getInt("surge_basis_points"), rs.getInt("tax_basis_points"),
        rs.getLong("base_fare_minor"), rs.getLong("distance_fare_minor"), rs.getLong("time_fare_minor"),
        rs.getLong("minimum_adjustment_minor"), rs.getLong("subtotal_minor"), rs.getLong("surge_minor"),
        rs.getLong("tax_minor"), rs.getLong("total_minor"), rs.getString("currency"),
        QuoteStatus.valueOf(rs.getString("status")), rs.getTimestamp("expires_at").toInstant(),
        rs.getString("request_fingerprint"), rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant(), rs.getLong("version"));
  }

  private Timestamp timestamp(Instant instant) {
    return instant == null ? null : Timestamp.from(instant);
  }

  private Instant instant(ResultSet rs, String column) throws SQLException {
    Timestamp timestamp = rs.getTimestamp(column);
    return timestamp == null ? null : timestamp.toInstant();
  }

  private Integer integer(ResultSet rs, String column) throws SQLException {
    int value = rs.getInt(column);
    return rs.wasNull() ? null : value;
  }
}
