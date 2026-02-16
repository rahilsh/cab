package in.rsh.cab.constants;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class AppConstantsTest {

  @Test
  void jsonMimeType_shouldReturnApplicationJson() {
    assertEquals("application/json", AppConstants.JSON_MIMETYPE);
  }

  @Test
  void charEncodingUtf8_shouldReturnUtf8() {
    assertEquals("UTF-8", AppConstants.CHAR_ENCODING_UTF8);
  }
}
