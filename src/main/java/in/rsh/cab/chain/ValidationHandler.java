package in.rsh.cab.chain;

public interface ValidationHandler<T> {

  ValidationHandler<T> setNext(ValidationHandler<T> handler);

  void validate(T request);
}
