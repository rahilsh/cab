package in.rsh.cab.payment.internal.persistence;

import in.rsh.cab.payment.DriverEarning;
import in.rsh.cab.payment.Payment;
import in.rsh.cab.payment.PaymentAccount;
import in.rsh.cab.payment.PaymentState;
import in.rsh.cab.payment.ProviderEvent;
import in.rsh.cab.payment.Refund;
import in.rsh.cab.payment.RefundAllocation;
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
             driver_share_minor, platform_share_minor, commission_basis_points,
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
  public Optional<UUID> findTenantForAccount(UUID accountId) {
    return jdbc.sql("SELECT routed_tenant FROM payment_account_routes WHERE payment_account_id = :id")
        .param("id", accountId).query(UUID.class).optional();
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
              AND provider_request_id IS NULL
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
  public void markCaptureSubmissionRetryable(
      UUID tenantId, UUID paymentId, String failureCode) {
    jdbc.sql("""
            UPDATE payment_attempts SET state = 'PENDING', failure_code = :failure,
              completed_at = NULL
            WHERE tenant_id = :tenantId AND payment_id = :paymentId AND operation = 'CAPTURE'
              AND state = 'PROCESSING' AND provider_request_id IS NULL
            """)
        .param("tenantId", tenantId).param("paymentId", paymentId).param("failure", failureCode)
        .update();
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
             SELECT id, payment_id, amount_minor, driver_share_minor, platform_share_minor,
                    currency, reason, state, provider_refund_id,
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
               provider_object_id, provider_version, amount_minor, currency, status, received_at,
               payment_id, refund_id, payout_id)
             VALUES (:id, :tenantId, :accountId, :eventId, :eventType, :objectId,
                     :providerVersion, :amount, :currency, 'RECEIVED', :receivedAt,
                     :paymentId, :refundId, :payoutId)
            ON CONFLICT (payment_account_id, provider_event_id) DO NOTHING
            """)
        .param("id", id).param("tenantId", account.tenantId()).param("accountId", account.id())
        .param("eventId", event.eventId()).param("eventType", event.type().name())
         .param("objectId", event.providerObjectId()).param("providerVersion", event.providerVersion())
         .param("amount", event.amountMinor()).param("currency", event.currency())
        .param("paymentId", event.paymentId()).param("refundId", event.refundId())
        .param("payoutId", event.payoutId())
        .param("receivedAt", Timestamp.from(receivedAt)).update() == 1;
  }

  @Override
  public boolean applyCaptureEvent(
      PaymentAccount account, ProviderEvent event, Instant now, boolean succeeded,
      int commissionBasisPoints) {
    int changed = jdbc.sql("""
             UPDATE payments SET state = :state, captured_minor = CASE WHEN :succeeded
                 THEN :amount ELSE captured_minor END, provider_payment_id = :providerId,
                 platform_share_minor = CASE WHEN :succeeded THEN
                   trunc((CAST(:amount AS numeric) * :commissionBasisPoints) / 10000)::bigint
                   ELSE platform_share_minor END,
                 driver_share_minor = CASE WHEN :succeeded THEN :amount -
                   trunc((CAST(:amount AS numeric) * :commissionBasisPoints) / 10000)::bigint
                   ELSE driver_share_minor END,
                 commission_basis_points = CASE WHEN :succeeded THEN :commissionBasisPoints
                   ELSE commission_basis_points END,
                 provider_version = :providerVersion, failure_code = :failure,
                version = version + 1, updated_at = :now
            WHERE tenant_id = :tenantId AND payment_account_id = :accountId AND id = :paymentId
              AND provider_version < :providerVersion
              AND (:currency IS NULL OR currency = :currency)
              AND (:amount IS NULL OR amount_minor = :amount)
              AND state IN ('CAPTURE_PENDING', 'FAILED')
            """)
         .param("state", succeeded ? "CAPTURED" : "FAILED").param("succeeded", succeeded)
        .param("commissionBasisPoints", commissionBasisPoints)
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
    RefundSplit split = null;
    if (succeeded) {
      PaymentSplit capture = jdbc.sql("""
              SELECT captured_minor, driver_share_minor, platform_share_minor
              FROM payments
              WHERE tenant_id = :tenantId AND id = :paymentId
                AND payment_account_id = :accountId AND state = 'CAPTURED'
              FOR UPDATE
              """)
          .param("tenantId", account.tenantId()).param("paymentId", event.paymentId())
          .param("accountId", account.id())
          .query((rs, row) -> new PaymentSplit(rs.getLong("captured_minor"),
              rs.getObject("driver_share_minor", Long.class),
              rs.getObject("platform_share_minor", Long.class))).optional().orElse(null);
      if (capture == null || capture.driverShare() == null || capture.platformShare() == null) {
        return false;
      }
      RefundTotals totals = jdbc.sql("""
              SELECT COALESCE(sum(amount_minor), 0) amount,
                     COALESCE(sum(driver_share_minor), 0) driver_share
              FROM refunds
              WHERE tenant_id = :tenantId AND payment_id = :paymentId AND state = 'SUCCEEDED'
                AND id <> :refundId
              """)
          .param("tenantId", account.tenantId()).param("paymentId", event.paymentId())
          .param("refundId", event.refundId())
          .query((rs, row) -> new RefundTotals(
              rs.getLong("amount"), rs.getLong("driver_share"))).single();
      RefundAllocation allocation = RefundAllocation.cumulative(
          capture.captured(), capture.driverShare(), totals.amount(), totals.driverShare(),
          event.amountMinor());
      split = new RefundSplit(allocation.driverShareMinor(), allocation.platformShareMinor());
    }
    return jdbc.sql("""
             UPDATE refunds r SET state = :state, provider_refund_id = :providerId,
               driver_share_minor = CASE WHEN :succeeded THEN :driverShare
                 ELSE r.driver_share_minor END,
               platform_share_minor = CASE WHEN :succeeded THEN :platformShare
                 ELSE r.platform_share_minor END,
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
        .param("succeeded", succeeded)
        .param("driverShare", split == null ? null : split.driverShare())
        .param("platformShare", split == null ? null : split.platformShare())
        .param("providerId", event.providerObjectId()).param("providerVersion", event.providerVersion())
        .param("failure", event.failureCode()).param("now", Timestamp.from(now))
        .param("tenantId", account.tenantId()).param("refundId", event.refundId())
        .param("paymentId", event.paymentId()).param("accountId", account.id())
        .param("currency", event.currency())
        .param("amount", event.amountMinor()).update() == 1;
  }

  @Override
  public boolean applyPayoutEvent(
      PaymentAccount account, ProviderEvent event, Instant now, boolean succeeded) {
    Optional<UUID> batchId = jdbc.sql("""
            UPDATE payouts SET state = :state, provider_payout_id = :providerId,
              provider_version = :providerVersion, failure_code = :failure,
              version = version + 1, updated_at = :now
            WHERE tenant_id = :tenantId AND payment_account_id = :accountId AND id = :payoutId
              AND provider_version < :providerVersion
              AND (:currency IS NULL OR currency = :currency)
              AND (:amount IS NULL OR amount_minor = :amount)
              AND (provider_payout_id IS NULL OR provider_payout_id = :providerId)
              AND state = 'PENDING'
            RETURNING settlement_batch_id
            """)
        .param("state", succeeded ? "PAID" : "FAILED")
        .param("providerId", event.providerObjectId()).param("providerVersion", event.providerVersion())
        .param("failure", event.failureCode()).param("now", Timestamp.from(now))
        .param("tenantId", account.tenantId()).param("accountId", account.id())
        .param("payoutId", event.payoutId()).param("currency", event.currency())
        .param("amount", event.amountMinor()).query(UUID.class).optional();
    if (batchId.isEmpty()) {
      return false;
    }
    jdbc.sql("""
            UPDATE payout_attempts SET state = :state, failure_code = :failure, completed_at = :now
            WHERE tenant_id = :tenantId AND payout_id = :payoutId
            """)
        .param("state", succeeded ? "SUCCEEDED" : "FAILED")
        .param("failure", event.failureCode()).param("now", Timestamp.from(now))
        .param("tenantId", account.tenantId()).param("payoutId", event.payoutId()).update();
    if (succeeded) {
      jdbc.sql("""
              UPDATE settlement_batches SET state = 'COMPLETED', completed_at = :now
              WHERE tenant_id = :tenantId AND id = :batchId AND state = 'PROCESSING'
                AND NOT EXISTS (SELECT 1 FROM payouts WHERE tenant_id = :tenantId
                  AND settlement_batch_id = :batchId AND state <> 'PAID')
              """)
          .param("tenantId", account.tenantId()).param("batchId", batchId.orElseThrow())
          .param("now", Timestamp.from(now)).update();
    } else {
      jdbc.sql("""
              UPDATE settlement_batches SET state = 'FAILED', completed_at = :now
              WHERE tenant_id = :tenantId AND id = :batchId AND state = 'PROCESSING'
              """)
          .param("tenantId", account.tenantId()).param("batchId", batchId.orElseThrow())
          .param("now", Timestamp.from(now)).update();
    }
    return true;
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
  public void postCaptureLedger(UUID tenantId, UUID paymentId, Instant now) {
    postMarketplaceLedger(
        tenantId, "PAYMENT_CAPTURE", paymentId, "Ride payment captured", now, """
        SELECT p.captured_minor amount, p.driver_share_minor, p.platform_share_minor,
               p.currency, r.driver_id account_id
        FROM payments p JOIN rides r ON r.tenant_id = p.tenant_id AND r.id = p.ride_id
        WHERE p.tenant_id = :tenantId AND p.id = :sourceId AND p.state = 'CAPTURED'
          AND p.driver_share_minor IS NOT NULL AND p.platform_share_minor IS NOT NULL
        """, false);
  }

  @Override
  public void postRefundLedger(UUID tenantId, UUID refundId, Instant now) {
    postMarketplaceLedger(
        tenantId, "PAYMENT_REFUND", refundId, "Ride payment refunded", now, """
        SELECT f.amount_minor amount, f.driver_share_minor, f.platform_share_minor,
               f.currency, r.driver_id account_id
        FROM refunds f JOIN payments p ON p.tenant_id = f.tenant_id AND p.id = f.payment_id
        JOIN rides r ON r.tenant_id = p.tenant_id AND r.id = p.ride_id
        WHERE f.tenant_id = :tenantId AND f.id = :sourceId AND f.state = 'SUCCEEDED'
          AND f.driver_share_minor IS NOT NULL AND f.platform_share_minor IS NOT NULL
        """, true);
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
      UUID tenantId, UUID actorId, UUID paymentAccountId, String currency, Instant now) {
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
    String batchState = total == 0 ? "COMPLETED" : "PROCESSING";
    jdbc.sql("""
            INSERT INTO settlement_batches
              (id, tenant_id, currency, state, total_minor, created_by_account_id, created_at, completed_at)
            VALUES (:id, :tenantId, :currency, :state, :total, :actorId, :now,
                    CASE WHEN :total = 0 THEN CAST(:now AS timestamptz) ELSE NULL END)
            """)
        .param("id", batchId).param("tenantId", tenantId).param("currency", currency)
        .param("state", batchState)
        .param("total", total).param("actorId", actorId).param("now", Timestamp.from(now)).update();
    List<SettlementBatch.Payout> payouts = balances.stream().map(balance -> {
      UUID payoutId = UUID.randomUUID();
      jdbc.sql("""
               INSERT INTO payouts
                 (id, tenant_id, settlement_batch_id, driver_id, amount_minor, currency, state,
                  payment_account_id, provider_version, version, created_at, updated_at)
               VALUES (:id, :tenantId, :batchId, :driverId, :amount, :currency, 'PENDING',
                       :accountId, 0, 0, :now, :now)
               """)
          .param("id", payoutId).param("tenantId", tenantId).param("batchId", batchId)
           .param("driverId", balance.driverId()).param("amount", balance.amount())
          .param("currency", currency).param("accountId", paymentAccountId)
          .param("now", Timestamp.from(now)).update();
      jdbc.sql("""
              INSERT INTO payout_attempts
                (id, tenant_id, payout_id, idempotency_key, state, created_at)
              VALUES (:id, :tenantId, :payoutId, :key, 'PENDING', :now)
              """)
          .param("id", UUID.randomUUID()).param("tenantId", tenantId)
          .param("payoutId", payoutId).param("key", "payout:" + payoutId)
          .param("now", Timestamp.from(now)).update();
      postSettlementLedger(tenantId, payoutId, balance, currency, now);
      return new SettlementBatch.Payout(payoutId, balance.driverId(), balance.amount(), currency,
          "PENDING", paymentAccountId, null, 0, null);
    }).toList();
    return new SettlementBatch(batchId, currency, batchState, total, now, payouts);
  }

  @Override
  public Optional<SettlementBatch.Payout> findPayout(UUID tenantId, UUID payoutId) {
    return jdbc.sql("""
            SELECT id, driver_id, amount_minor, currency, state, payment_account_id,
                   provider_payout_id, provider_version, failure_code
            FROM payouts WHERE tenant_id = :tenantId AND id = :payoutId
            """)
        .param("tenantId", tenantId).param("payoutId", payoutId)
        .query(this::mapPayout).optional();
  }

  @Override
  public boolean markPayoutProcessing(UUID tenantId, UUID payoutId) {
    return jdbc.sql("""
            UPDATE payout_attempts SET state = 'PROCESSING'
            WHERE tenant_id = :tenantId AND payout_id = :payoutId
              AND state IN ('PENDING', 'PROCESSING') AND provider_request_id IS NULL
            """)
        .param("tenantId", tenantId).param("payoutId", payoutId).update() == 1;
  }

  @Override
  public void markPayoutSubmitted(
      UUID tenantId, UUID payoutId, String providerPayoutId, String providerRequestId, Instant now) {
    jdbc.sql("""
            UPDATE payouts SET provider_payout_id = :providerId, updated_at = :now
            WHERE tenant_id = :tenantId AND id = :payoutId AND state = 'PENDING'
              AND provider_payout_id IS NULL
            """)
        .param("tenantId", tenantId).param("payoutId", payoutId)
        .param("providerId", providerPayoutId).param("now", Timestamp.from(now)).update();
    jdbc.sql("""
            UPDATE payout_attempts SET provider_request_id = :requestId
            WHERE tenant_id = :tenantId AND payout_id = :payoutId AND state = 'PROCESSING'
            """)
        .param("tenantId", tenantId).param("payoutId", payoutId)
        .param("requestId", providerRequestId).update();
  }

  @Override
  public void markPayoutSubmissionRetryable(
      UUID tenantId, UUID payoutId, String failureCode) {
    jdbc.sql("""
            UPDATE payout_attempts SET state = 'PENDING', failure_code = :failure
            WHERE tenant_id = :tenantId AND payout_id = :payoutId
              AND state = 'PROCESSING' AND provider_request_id IS NULL
            """)
        .param("tenantId", tenantId).param("payoutId", payoutId)
        .param("failure", failureCode).update();
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
      String sourceSql, boolean refund) {
    jdbc.sql("SELECT id FROM tenants WHERE id = :tenantId FOR UPDATE")
        .param("tenantId", tenantId).query(UUID.class).single();
    Optional<LedgerSource> source = jdbc.sql(sourceSql).param("tenantId", tenantId)
        .param("sourceId", sourceId).query((rs, row) -> new LedgerSource(
            rs.getLong("amount"), rs.getLong("driver_share_minor"),
            rs.getLong("platform_share_minor"), rs.getString("currency"),
            rs.getObject("account_id", UUID.class)))
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
    if (refund) {
      if (value.driverShare() > 0) {
        insertEntry(tenantId, transactionId, "DRIVER_PAYABLE", value.accountId(),
            value.driverShare(), 0, value.currency(), now);
      }
      if (value.platformShare() > 0) {
        insertEntry(tenantId, transactionId, "PLATFORM_REVENUE", null,
            value.platformShare(), 0, value.currency(), now);
      }
      insertEntry(tenantId, transactionId, "PROVIDER_RECEIVABLE", null,
          0, value.amount(), value.currency(), now);
    } else {
      insertEntry(tenantId, transactionId, "PROVIDER_RECEIVABLE", null,
          value.amount(), 0, value.currency(), now);
      if (value.driverShare() > 0) {
        insertEntry(tenantId, transactionId, "DRIVER_PAYABLE", value.accountId(),
            0, value.driverShare(), value.currency(), now);
      }
      if (value.platformShare() > 0) {
        insertEntry(tenantId, transactionId, "PLATFORM_REVENUE", null,
            0, value.platformShare(), value.currency(), now);
      }
    }
  }

  @Override
  public void postPayoutReleaseLedger(UUID tenantId, UUID payoutId, Instant now) {
    jdbc.sql("SELECT id FROM tenants WHERE id = :tenantId FOR UPDATE")
        .param("tenantId", tenantId).query(UUID.class).single();
    Optional<Balance> payout = jdbc.sql("""
            SELECT driver_id account_id, amount_minor amount
            FROM payouts WHERE tenant_id = :tenantId AND id = :payoutId AND state = 'FAILED'
            """)
        .param("tenantId", tenantId).param("payoutId", payoutId)
        .query((rs, row) -> new Balance(
            rs.getObject("account_id", UUID.class), rs.getLong("amount"))).optional();
    if (payout.isEmpty()) {
      return;
    }
    UUID transactionId = UUID.randomUUID();
    int inserted = jdbc.sql("""
            INSERT INTO ledger_transactions
              (id, tenant_id, source_type, source_id, description, created_at)
            VALUES (:id, :tenantId, 'PAYOUT_RELEASE', :payoutId,
                    'Failed driver payout reservation released', :now)
            ON CONFLICT (tenant_id, source_type, source_id) DO NOTHING
            """)
        .param("id", transactionId).param("tenantId", tenantId).param("payoutId", payoutId)
        .param("now", Timestamp.from(now)).update();
    if (inserted == 0) {
      return;
    }
    Balance value = payout.orElseThrow();
    String currency = jdbc.sql("SELECT currency FROM payouts WHERE tenant_id = :tenantId AND id = :id")
        .param("tenantId", tenantId).param("id", payoutId).query(String.class).single();
    insertEntry(tenantId, transactionId, "PAYOUT_CLEARING", null,
        value.amount(), 0, currency, now);
    insertEntry(tenantId, transactionId, "DRIVER_PAYABLE", value.driverId(),
        0, value.amount(), currency, now);
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
        rs.getLong("authorized_minor"), rs.getLong("captured_minor"),
        rs.getObject("driver_share_minor", Long.class),
        rs.getObject("platform_share_minor", Long.class),
        rs.getObject("commission_basis_points", Integer.class), rs.getString("currency"),
        PaymentState.valueOf(rs.getString("state")), rs.getString("provider_payment_id"),
        rs.getLong("provider_version"), rs.getLong("version"), rs.getString("failure_code"),
        rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());
  }

  private Refund mapRefund(ResultSet rs, int row) throws SQLException {
    return new Refund(rs.getObject("id", UUID.class), rs.getObject("payment_id", UUID.class),
        rs.getLong("amount_minor"), rs.getObject("driver_share_minor", Long.class),
        rs.getObject("platform_share_minor", Long.class), rs.getString("currency"), rs.getString("reason"),
        RefundState.valueOf(rs.getString("state")), rs.getString("provider_refund_id"),
        rs.getLong("provider_version"), rs.getLong("version"),
        rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());
  }

  private SettlementBatch.Payout mapPayout(ResultSet rs, int row) throws SQLException {
    return new SettlementBatch.Payout(rs.getObject("id", UUID.class),
        rs.getObject("driver_id", UUID.class), rs.getLong("amount_minor"),
        rs.getString("currency"), rs.getString("state"),
        rs.getObject("payment_account_id", UUID.class), rs.getString("provider_payout_id"),
        rs.getLong("provider_version"), rs.getString("failure_code"));
  }

  private record LedgerSource(
      long amount, long driverShare, long platformShare, String currency, UUID accountId) {}

  private record Balance(UUID driverId, long amount) {}

  private record PaymentSplit(long captured, Long driverShare, Long platformShare) {}

  private record RefundTotals(long amount, long driverShare) {}

  private record RefundSplit(long driverShare, long platformShare) {}
}
