package in.rsh.cab.user.util;

import java.util.UUID;

public class IDUtil {

  public static String generateID() {
    return UUID.randomUUID().toString();
  }
}
