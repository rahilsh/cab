package in.rsh.cab.operations;

public interface OutboxConsumer {

  boolean process(OutboxEvent event);
}
