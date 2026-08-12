package in.rsh.cab.payment.internal.persistence;

import in.rsh.cab.payment.DriverEarning;
import in.rsh.cab.payment.Payment;
import in.rsh.cab.payment.PaymentAccount;
import in.rsh.cab.payment.PaymentState;
import in.rsh.cab.payment.ProviderEvent;
import in.rsh.cab.payment.Refund;
import in.rsh.cab.payment.RefundState;
import in.rsh.cab.payment.SettlementBatch;
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
public class JdbcPaymentRepository implements PaymentRepository {

  private static final String PAYMENT_SELECT = """
      SELECT id, ride_id, rider_account_id, amount_minor, authorized_minor, captured_minor,
             currency, state, provider_payment_id, provider_version, version, failure_code,
             created_at, updated_at FROM payments
      """;
  private final JdbcClient jdbc;

  public JdbcPaymentRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public PaymentAccount activeAccount(
      UUID tenantId, String provider, String configReference, String secretReference, Instant now) {
    jdbc.sql("""
            INSERT INTO payment_accounts
              (id, tenant_id, provider, config_reference, webhook_secret_reference, active,
               created_at, updated_at)
            VALUES (:id, :tenantId, :provider, :configReference, :secretReference, true, :now, :now)
            ON CONFLICT (tenant_id, provider) DO NOTHING
            """)
        .param("id", UUID.randomUUID()).param("tenantId", tenantId).param("provider", provider)
        .param("configReference", configReference).param("secretReference", secretReference)
        .param("now", Timestamp.from(now)).update();
    return jdbc.sql("""
            SELECT id, tenant_id, provider, config_reference, webhook_secret_reference, active
            FROM payment_accounts WHERE tenant_id = :tenantId AND provider = :provider AND active
            """)
        .param("tenantId", tenantId).param("provider", provider).query(this::mapAccount).single();
  }

  @Override
  public Optional<PaymentAccount> findAccount(UUID accountId) {
    return jdbc.sql("""
            SELECT id, tenant_id, provider, config_reference, webhook_secret_reference, active
            FROM payment_accounts WHERE id = :id AND active
            """)
        .param("id", accountId).query(this::mapAccount).optional();
  }

  @Override
  public Optional<PaymentAccount> findAccountForPayment(UUID tenantId, UUID paymentId) {
    return jdbc.sql("""
            SELECT a.id, a.tenant_id, a.provider, a.config_reference,
                   a.webhook_secret_reference, a.active
            FROM payment_accounts a JOIN payments p
              ON p.tenant_id = a.tenant_id AND p.payment_account_id = a.id
            WHERE p.tenant_id = :tenantId AND p.id = :paymentId AND a.active
            """)
        .param("tenantId", tenantId).param("paymentId", paymentId)
        .query(this::mapAccount).optional();
  }

  @Override
  public void insertCaptureRequest(UUID tenantId, UUID accountId, Payment payment, UUID attemptId) {
    jdbc.sql("""
            INSERT INTO payments
              (id, tenant_id, payment_account_id, ride_id, rider_account_id, amount_minor,
               authorized_minor, captured_minor, currency, state, provider_version, version,
               created_at, updated_at)
            VALUES (:id, :tenantId, :accountId, :rideId, :riderId, :amount, :amount, 0,
                    :currency, 'CAPTURE_PENDING', 0, 0, :now, :now)
            ON CONFLICT (tenant_id, ride_id) DO NOTHING
            """)
        .param("id", payment.id()).param("tenantId", tenantId).param("accountId", accountId)
        .param("rideId", payment.rideId()).param("riderId", payment.riderAccountId())
        .param("amount", payment.amountMinor()).param("currency", payment.currency())
        .param("now", Timestamp.from(payment.createdAt())).update();
    Payment stored = jdbc.sql(PAYMENT_SELECT + " WHERE tenant_id = :tenantId AND ride_id = :rideId")
        .param("tenantId", tenantId).param("rideId", payment.rideId()).query(this::mapPayment).single();
    jdbc.sql("""
            INSERT INTO payment_attempts
              (id, tenant_id, payment_id, operation, idempotency_key, state, created_at)
            VALUES (:id, :tenantId, :paymentId, 'CAPTURE', :key, 'PENDING', :now)
            ON CONFLICT (tenant_id, payment_id, operation, idempotency_key) DO NOTHING
            """)
        .param("id", attemptId).param("tenantId", tenantId).param("paymentId", stored.id())
        .param("key", "capture:" + stored.id()).param("now", Timestamp.from(payment.createdAt())).update();
  }

  @Override
  public Optional<Payment> findOwn(UUID tenantId, UUID riderAccountId, UUID paymentId) {
    return jdbc.sql(PAYMENT_SELECT
            + " WHERE tenant_id = :tenantId AND rider_account_id = :riderId AND id = :id")
        .param("tenantId", tenantId).param("riderId", riderAccountId).param("id", paymentId)
        .query(this::mapPayment).optional();
  }

  @Override
  public Optional<Payment> findByRide(UUID tenantId, UUID riderAccountId, UUID rideId) {
    return jdbc.sql(PAYMENT_SELECT
            + " WHERE tenant_id = :tenantId AND rider_account_id = :riderId AND ride_id = :rideId")
        .param("tenantId", tenantId).param("riderId", riderAccountId).param("rideId", rideId)
        .query(this::mapPayment).optional();
  }

  @Override
  public Optional<Payment> find(UUID tenantId, UUID paymentId) {
    return jdbc.sql(PAYMENT_SELECT + " WHERE tenant_id = :tenantId AND id = :id")
        .param("tenantId", tenantId).param("id", paymentId).query(this::mapPayment).optional();
  }

  @Override
  public boolean markCaptureProcessing(UUID tenantId, UUID paymentId) {
    return jdbc.sql("""
            UPDATE payment_attempts SET state = 'PROCESSING'
            WHERE tenant_id = :tenantId AND payment_id = :paymentId
              AND operation = 'CAPTURE' AND state IN ('PENDING', 'PROCESSING')
            """)
        .param("tenantId", tenantId).param("paymentId", paymentId).update() == 1;
  }

  @Override
  public void markCaptureSubmitted(
      UUID tenantId, UUID paymentId, String providerPaymentId, String providerRequestId) {
    jdbc.sql("""
            UPDATE payments SET provider_payment_id = :providerPaymentId
            WHERE tenant_id = :tenantId AND id = :paymentId AND state = 'CAPTURE_PENDING'
            """)
        .param("tenantId", tenantId).param("paymentId", paymentId)
        .param("providerPaymentId", providerPaymentId).update();
    jdbc.sql("""
            UPDATE payment_attempts SET provider_request_id = :requestId
            WHERE tenant_id = :tenantId AND payment_id = :paymentId
              AND operation = 'CAPTURE' AND state = 'PROCESSING'
            """)
        .param("tenantId", tenantId).param("paymentId", paymentId)
        .param("requestId", providerRequestId).update();
  }

  @Override
  public void markCaptureSubmissionFailed(
      UUID tenantId, UUID paymentId, String failureCode, Instant now) {
    jdbc.sql("""
            UPDATE payments SET state = 'FAILED', failure_code = :failure, version = version + 1,
              updated_at = :now WHERE tenant_id = :tenantId AND id = :paymentId
              AND state = 'CAPTURE_PENDING'
            """)
        .param("tenantId", tenantId).param("paymentId", paymentId).param("failure", failureCode)
        .param("now", Timestamp.from(now)).update();
    jdbc.sql("""
            UPDATE payment_attempts SET state = 'FAILED', failure_code = :failure, completed_at = :now
            WHERE tenant_id = :tenantId AND payment_id = :paymentId AND operation = 'CAPTURE'
            """)
        .param("tenantId", tenantId).param("paymentId", paymentId).param("failure", failureCode)
        .param("now", Timestamp.from(now)).update();
  }

  @Override
  public void insertRefund(UUID tenantId, Refund refund, UUID requesterId) {
    jdbc.sql("""
            INSERT INTO refunds
              (id, tenant_id, payment_id, amount_minor, currency, reason, state,
               provider_version, version, requested_by_account_id, created_at, updated_at)
            VALUES (:id, :tenantId, :paymentId, :amount, :currency, :reason, 'PENDING',
                    0, 0, :requesterId, :now, :now)
            """)
        .param("id", refund.id()).param("tenantId", tenantId).param("paymentId", refund.paymentId())
        .param("amount", refund.amountMinor()).param("currency", refund.currency())
        .param("reason", refund.reason()).param("requesterId", requesterId)
        .param("now", Timestamp.from(refund.createdAt())).update();
  }

  @Override
  public Optional<Refund> findRefund(UUID tenantId, UUID refundId) {
    return jdbc.sql("""
            SELECT id, payment_id, amount_minor, currency, reason, state, provider_refund_id,
                   provider_version, version, created_at, updated_at
            FROM refunds WHERE tenant_id = :tenantId AND id = :id
            """)
        .param("tenantId", tenantId).param("id", refundId).query(this::mapRefund).optional();
  }

  @Override
  public long committedRefundMinor(UUID tenantId, UUID paymentId) {
    return jdbc.sql("""
            SELECT COALESCE(sum(amount_minor), 0) FROM refunds
            WHERE tenant_id = :tenantId AND payment_id = :paymentId
              AND state IN ('REQUESTED', 'PENDING', 'SUCCEEDED')
            """)
        .param("tenantId", tenantId).param("paymentId", paymentId).query(Long.class).single();
  }

  @Override
  public boolean markRefundProcessing(UUID tenantId, UUID refundId) {
    return jdbc.sql("""
            UPDATE refunds SET state = 'PENDING'
            WHERE tenant_id = :tenantId AND id = :id AND state IN ('REQUESTED', 'PENDING')
              AND provider_refund_id IS NULL
            """)
        .param("tenantId", tenantId).param("id", refundId).update() == 1;
  }

  @Override
  public void markRefundSubmitted(
      UUID tenantId, UUID refundId, String providerRefundId, Instant now) {
    jdbc.sql("""
            UPDATE refunds SET provider_refund_id = :providerId, updated_at = :now
            WHERE tenant_id = :tenantId AND id = :id AND state = 'PENDING'
            """)
        .param("tenantId", tenantId).param("id", refundId).param("providerId", providerRefundId)
        .param("now", Timestamp.from(now)).update();
  }

  @Override
  public boolean insertProviderEvent(
      UUID id, PaymentAccount account, ProviderEvent event, Instant receivedAt) {
    return jdbc.sql("""
            INSERT INTO provider_events
              (id, tenant_id, payment_account_id, provider_event_id, event_type,
               provider_object_id, provider_version, amount_minor, currency, status, received_at)
            VALUES (:id, :tenantId, :accountId, :eventId, :eventType, :objectId,
                    :providerVersion, :amount, :currency, 'RECEIVED', :receivedAt)
            ON CONFLICT (payment_account_id, provider_event_id) DO NOTHING
            """)
        .param("id", id).param("tenantId", account.tenantId()).param("accountId", account.id())
        .param("eventId", event.eventId()).param("eventType", event.type().name())
        .param("objectId", event.providerObjectId()).param("providerVersion", event.providerVersion())
        .param("amount", event.amountMinor()).param("currency", event.currency())
        .param("receivedAt", Timestamp.from(receivedAt)).update() == 1;
  }

  @Override
  public boolean applyCaptureEvent(
      PaymentAccount account, ProviderEvent event, Instant now, boolean succeeded) {
    int changed = jdbc.sql("""
            UPDATE payments SET state = :state, captured_minor = CASE WHEN :succeeded
                THEN :amount ELSE captured_minor END, provider_payment_id = :providerId,
                provider_version = :providerVersion, failure_code = :failure,
                version = version + 1, updated_at = :now
            WHERE tenant_id = :tenantId AND payment_account_id = :accountId AND id = :paymentId
              AND provider_version < :providerVersion
              AND (:currency IS NULL OR currency = :currency)
              AND (:amount IS NULL OR amount_minor = :amount)
              AND state IN ('CAPTURE_PENDING', 'FAILED')
            """)
        .param("state", succeeded ? "CAPTURED" : "FAILED").param("succeeded", succeeded)
        .param("amount", event.amountMinor()).param("providerId", event.providerObjectId())
        .param("providerVersion", event.providerVersion()).param("failure", event.failureCode())
        .param("now", Timestamp.from(now)).param("tenantId", account.tenantId())
        .param("accountId", account.id()).param("paymentId", event.paymentId())
        .param("currency", event.currency()).update();
    if (changed == 1) {
      jdbc.sql("""
              UPDATE payment_attempts SET state = :state, failure_code = :failure, completed_at = :now
              WHERE tenant_id = :tenantId AND payment_id = :paymentId AND operation = 'CAPTURE'
              """)
          .param("state", succeeded ? "SUCCEEDED" : "FAILED").param("failure", event.failureCode())
          .param("now", Timestamp.from(now)).param("tenantId", account.tenantId())
          .param("paymentId", event.paymentId()).update();
    }
    return changed == 1;
  }

  @Override
  public boolean applyRefundEvent(
      PaymentAccount account, ProviderEvent event, Instant now, boolean succeeded) {
    return jdbc.sql("""
            UPDATE refunds r SET state = :state, provider_refund_id = :providerId,
              provider_version = :providerVersion, failure_code = :failure,
              version = r.version + 1, updated_at = :now
            FROM payments p
            WHERE r.tenant_id = :tenantId AND r.id = :refundId
              AND p.tenant_id = r.tenant_id AND p.id = r.payment_id
              AND p.id = :paymentId
              AND p.payment_account_id = :accountId AND r.provider_version < :providerVersion
              AND (:currency IS NULL OR r.currency = :currency)
              AND (:amount IS NULL OR r.amount_minor = :amount)
              AND r.state IN ('REQUESTED', 'PENDING', 'FAILED')
            """)
        .param("state", succeeded ? "SUCCEEDED" : "FAILED")
        .param("providerId", event.providerObjectId()).param("providerVersion", event.providerVersion())
        .param("failure", event.failureCode()).param("now", Timestamp.from(now))
        .param("tenantId", account.tenantId()).param("refundId", event.refundId())
        .param("paymentId", event.paymentId()).param("accountId", account.id())
        .param("currency", event.currency())
        .param("amount", event.amountMinor()).update() == 1;
  }

  @Override
  public void markProviderEvent(UUID accountId, String eventId, boolean applied, Instant now) {
    jdbc.sql("""
            UPDATE provider_events SET status = :status, processed_at = :now
            WHERE payment_account_id = :accountId AND provider_event_id = :eventId
            """)
        .param("status", applied ? "APPLIED" : "IGNORED").param("now", Timestamp.from(now))
        .param("accountId", accountId).param("eventId", eventId).update();
  }

  @Override
  public void postCaptureLedger(
      UUID tenantId, UUID paymentId, int commissionBasisPoints, Instant now) {
    postMarketplaceLedger(
        tenantId, "PAYMENT_CAPTURE", paymentId, "Ride payment captured", now, """
        SELECT p.captured_minor amount, p.currency, r.driver_id account_id
        FROM payments p JOIN rides r ON r.tenant_id = p.tenant_id AND r.id = p.ride_id
        WHERE p.tenant_id = :tenantId AND p.id = :sourceId AND p.state = 'CAPTURED'
        """, commissionBasisPoints, false);
  }

  @Override
  public void postRefundLedger(
      UUID tenantId, UUID refundId, int commissionBasisPoints, Instant now) {
    postMarketplaceLedger(
        tenantId, "PAYMENT_REFUND", refundId, "Ride payment refunded", now, """
        SELECT f.amount_minor amount, f.currency, r.driver_id account_id
        FROM refunds f JOIN payments p ON p.tenant_id = f.tenant_id AND p.id = f.payment_id
        JOIN rides r ON r.tenant_id = p.tenant_id AND r.id = p.ride_id
        WHERE f.tenant_id = :tenantId AND f.id = :sourceId AND f.state = 'SUCCEEDED'
        """, commissionBasisPoints, true);
  }

  @Override
  public List<DriverEarning> earnings(UUID tenantId, UUID driverId) {
    return jdbc.sql("""
            SELECT currency, sum(credit_minor - debit_minor) available_minor
            FROM ledger_entries WHERE tenant_id = :tenantId AND account_type = 'DRIVER_PAYABLE'
              AND account_id = :driverId GROUP BY currency ORDER BY currency
            """)
        .param("tenantId", tenantId).param("driverId", driverId)
        .query((rs, row) -> new DriverEarning(rs.getString("currency"), rs.getLong("available_minor")))
        .list();
  }

  @Override
  public SettlementBatch createSettlement(
      UUID tenantId, UUID actorId, String currency, Instant now) {
    jdbc.sql("SELECT id FROM tenants WHERE id = :tenantId FOR UPDATE")
        .param("tenantId", tenantId).query(UUID.class).single();
    List<Balance> balances = jdbc.sql("""
            SELECT account_id, sum(credit_minor - debit_minor) amount
            FROM ledger_entries WHERE tenant_id = :tenantId AND account_type = 'DRIVER_PAYABLE'
              AND currency = :currency GROUP BY account_id
            HAVING sum(credit_minor - debit_minor) > 0 ORDER BY account_id
            """)
        .param("tenantId", tenantId).param("currency", currency)
        .query((rs, row) -> new Balance(rs.getObject("account_id", UUID.class), rs.getLong("amount")))
        .list();
    UUID batchId = UUID.randomUUID();
    long total = balances.stream().mapToLong(Balance::amount).sum();
    jdbc.sql("""
            INSERT INTO settlement_batches
              (id, tenant_id, currency, state, total_minor, created_by_account_id, created_at, completed_at)
            VALUES (:id, :tenantId, :currency, 'COMPLETED', :total, :actorId, :now, :now)
            """)
        .param("id", batchId).param("tenantId", tenantId).param("currency", currency)
        .param("total", total).param("actorId", actorId).param("now", Timestamp.from(now)).update();
    List<SettlementBatch.Payout> payouts = balances.stream().map(balance -> {
      UUID payoutId = UUID.randomUUID();
      jdbc.sql("""
              INSERT INTO payouts
                (id, tenant_id, settlement_batch_id, driver_id, amount_minor, currency, state,
                 version, created_at, updated_at)
              VALUES (:id, :tenantId, :batchId, :driverId, :amount, :currency, 'PAID', 0, :now, :now)
              """)
          .param("id", payoutId).param("tenantId", tenantId).param("batchId", batchId)
          .param("driverId", balance.driverId()).param("amount", balance.amount())
          .param("currency", currency).param("now", Timestamp.from(now)).update();
      postSettlementLedger(tenantId, payoutId, balance, currency, now);
      return new SettlementBatch.Payout(payoutId, balance.driverId(), balance.amount(), currency, "PAID");
    }).toList();
    return new SettlementBatch(batchId, currency, "COMPLETED", total, now, payouts);
  }

  @Override
  public List<SettlementBatch> settlements(UUID tenantId) {
    return jdbc.sql("""
            SELECT id, currency, state, total_minor, created_at FROM settlement_batches
            WHERE tenant_id = :tenantId ORDER BY created_at DESC, id
            """)
        .param("tenantId", tenantId).query((rs, row) -> new SettlementBatch(
            rs.getObject("id", UUID.class), rs.getString("currency"), rs.getString("state"),
            rs.getLong("total_minor"), rs.getTimestamp("created_at").toInstant(), List.of())).list();
  }

  private void postMarketplaceLedger(
      UUID tenantId, String sourceType, UUID sourceId, String description, Instant now,
      String sourceSql, int commissionBasisPoints, boolean refund) {
    Optional<LedgerSource> source = jdbc.sql(sourceSql).param("tenantId", tenantId)
        .param("sourceId", sourceId).query((rs, row) -> new LedgerSource(
            rs.getLong("amount"), rs.getString("currency"), rs.getObject("account_id", UUID.class)))
        .optional();
    if (source.isEmpty()) {
      return;
    }
    UUID transactionId = UUID.randomUUID();
    int inserted = jdbc.sql("""
            INSERT INTO ledger_transactions
              (id, tenant_id, source_type, source_id, description, created_at)
            VALUES (:id, :tenantId, :sourceType, :sourceId, :description, :now)
            ON CONFLICT (tenant_id, source_type, source_id) DO NOTHING
            """)
        .param("id", transactionId).param("tenantId", tenantId).param("sourceType", sourceType)
        .param("sourceId", sourceId).param("description", description)
        .param("now", Timestamp.from(now)).update();
    if (inserted == 0) {
      return;
    }
    LedgerSource value = source.orElseThrow();
    long platformAmount = java.math.BigInteger.valueOf(value.amount())
        .multiply(java.math.BigInteger.valueOf(commissionBasisPoints))
        .divide(java.math.BigInteger.valueOf(10_000))
        .longValueExact();
    long driverAmount = Math.subtractExact(value.amount(), platformAmount);
    if (refund) {
      insertEntry(tenantId, transactionId, "DRIVER_PAYABLE", value.accountId(),
          driverAmount, 0, value.currency(), now);
      if (platformAmount > 0) {
        insertEntry(tenantId, transactionId, "PLATFORM_REVENUE", null,
            platformAmount, 0, value.currency(), now);
      }
      insertEntry(tenantId, transactionId, "PROVIDER_RECEIVABLE", null,
          0, value.amount(), value.currency(), now);
    } else {
      insertEntry(tenantId, transactionId, "PROVIDER_RECEIVABLE", null,
          value.amount(), 0, value.currency(), now);
      insertEntry(tenantId, transactionId, "DRIVER_PAYABLE", value.accountId(),
          0, driverAmount, value.currency(), now);
      if (platformAmount > 0) {
        insertEntry(tenantId, transactionId, "PLATFORM_REVENUE", null,
            0, platformAmount, value.currency(), now);
      }
    }
  }

  private void postSettlementLedger(
      UUID tenantId, UUID payoutId, Balance balance, String currency, Instant now) {
    UUID transactionId = UUID.randomUUID();
    jdbc.sql("""
            INSERT INTO ledger_transactions
              (id, tenant_id, source_type, source_id, description, created_at)
            VALUES (:id, :tenantId, 'PAYOUT', :payoutId, 'Driver payout', :now)
            """)
        .param("id", transactionId).param("tenantId", tenantId).param("payoutId", payoutId)
        .param("now", Timestamp.from(now)).update();
    insertEntry(tenantId, transactionId, "DRIVER_PAYABLE", balance.driverId(),
        balance.amount(), 0, currency, now);
    insertEntry(tenantId, transactionId, "PAYOUT_CLEARING", null,
        0, balance.amount(), currency, now);
  }

  private void insertEntry(
      UUID tenantId, UUID transactionId, String accountType, UUID accountId,
      long debit, long credit, String currency, Instant now) {
    jdbc.sql("""
            INSERT INTO ledger_entries
              (id, tenant_id, transaction_id, account_type, account_id,
               debit_minor, credit_minor, currency, created_at)
            VALUES (:id, :tenantId, :transactionId, :accountType, :accountId,
                    :debit, :credit, :currency, :now)
            """)
        .param("id", UUID.randomUUID()).param("tenantId", tenantId)
        .param("transactionId", transactionId).param("accountType", accountType)
        .param("accountId", accountId).param("debit", debit).param("credit", credit)
        .param("currency", currency).param("now", Timestamp.from(now)).update();
  }

  private PaymentAccount mapAccount(ResultSet rs, int row) throws SQLException {
    return new PaymentAccount(rs.getObject("id", UUID.class), rs.getObject("tenant_id", UUID.class),
        rs.getString("provider"), rs.getString("config_reference"),
        rs.getString("webhook_secret_reference"), rs.getBoolean("active"));
  }

  private Payment mapPayment(ResultSet rs, int row) throws SQLException {
    return new Payment(rs.getObject("id", UUID.class), rs.getObject("ride_id", UUID.class),
        rs.getObject("rider_account_id", UUID.class), rs.getLong("amount_minor"),
        rs.getLong("authorized_minor"), rs.getLong("captured_minor"), rs.getString("currency"),
        PaymentState.valueOf(rs.getString("state")), rs.getString("provider_payment_id"),
        rs.getLong("provider_version"), rs.getLong("version"), rs.getString("failure_code"),
        rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());
  }

  private Refund mapRefund(ResultSet rs, int row) throws SQLException {
    return new Refund(rs.getObject("id", UUID.class), rs.getObject("payment_id", UUID.class),
        rs.getLong("amount_minor"), rs.getString("currency"), rs.getString("reason"),
        RefundState.valueOf(rs.getString("state")), rs.getString("provider_refund_id"),
        rs.getLong("provider_version"), rs.getLong("version"),
        rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());
  }

  private record LedgerSource(long amount, String currency, UUID accountId) {}

  private record Balance(UUID driverId, long amount) {}
}
