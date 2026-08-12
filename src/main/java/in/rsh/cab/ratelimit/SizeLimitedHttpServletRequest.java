package in.rsh.cab.ratelimit;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

final class SizeLimitedHttpServletRequest extends HttpServletRequestWrapper {

  private final byte[] body;
  private ServletInputStream inputStream;
  private BufferedReader reader;

  SizeLimitedHttpServletRequest(HttpServletRequest request, long maximumBytes) throws IOException {
    super(request);
    this.body =
        new SizeLimitedServletInputStream(request.getInputStream(), maximumBytes).readAllBytes();
  }

  @Override
  public ServletInputStream getInputStream() throws IOException {
    if (reader != null) {
      throw new IllegalStateException("getReader() has already been called for this request");
    }
    if (inputStream == null) {
      inputStream = new CachedBodyServletInputStream(body);
    }
    return inputStream;
  }

  @Override
  public BufferedReader getReader() throws IOException {
    if (reader != null) {
      return reader;
    }
    if (inputStream != null) {
      throw new IllegalStateException("getInputStream() has already been called for this request");
    }
    Charset charset =
        getCharacterEncoding() == null
            ? StandardCharsets.ISO_8859_1
            : Charset.forName(getCharacterEncoding());
    inputStream = new CachedBodyServletInputStream(body);
    reader = new BufferedReader(new InputStreamReader(inputStream, charset));
    return reader;
  }

  private static final class CachedBodyServletInputStream extends ServletInputStream {

    private final ByteArrayInputStream delegate;

    private CachedBodyServletInputStream(byte[] body) {
      this.delegate = new ByteArrayInputStream(body);
    }

    @Override
    public int read() {
      return delegate.read();
    }

    @Override
    public int read(byte[] bytes, int offset, int length) {
      return delegate.read(bytes, offset, length);
    }

    @Override
    public int available() {
      return delegate.available();
    }

    @Override
    public boolean isFinished() {
      return delegate.available() == 0;
    }

    @Override
    public boolean isReady() {
      return true;
    }

    @Override
    public void setReadListener(ReadListener listener) {
      try {
        if (!isFinished()) {
          listener.onDataAvailable();
        }
        if (isFinished()) {
          listener.onAllDataRead();
        }
      } catch (IOException exception) {
        listener.onError(exception);
      }
    }
  }

  private static final class SizeLimitedServletInputStream extends ServletInputStream {

    private final ServletInputStream delegate;
    private final long maximumBytes;
    private long bytesRead;

    private SizeLimitedServletInputStream(ServletInputStream delegate, long maximumBytes) {
      this.delegate = delegate;
      this.maximumBytes = maximumBytes;
    }

    @Override
    public int read() throws IOException {
      int value = delegate.read();
      if (value != -1 && ++bytesRead > maximumBytes) {
        throw new PayloadTooLargeException();
      }
      return value;
    }

    @Override
    public int read(byte[] bytes, int offset, int length) throws IOException {
      if (length == 0) {
        return 0;
      }
      long remaining = maximumBytes - bytesRead;
      int boundedLength = remaining >= length ? length : (int) remaining + 1;
      int read = delegate.read(bytes, offset, boundedLength);
      if (read != -1) {
        bytesRead += read;
        if (bytesRead > maximumBytes) {
          throw new PayloadTooLargeException();
        }
      }
      return read;
    }

    @Override
    public long skip(long count) throws IOException {
      long remaining = maximumBytes - bytesRead;
      long skipped = delegate.skip(remaining >= count ? count : remaining + 1);
      bytesRead += skipped;
      if (bytesRead > maximumBytes) {
        throw new PayloadTooLargeException();
      }
      return skipped;
    }

    @Override
    public int available() throws IOException {
      return delegate.available();
    }

    @Override
    public boolean isFinished() {
      return delegate.isFinished();
    }

    @Override
    public boolean isReady() {
      return delegate.isReady();
    }

    @Override
    public void setReadListener(ReadListener listener) {
      delegate.setReadListener(listener);
    }

    @Override
    public void close() throws IOException {
      delegate.close();
    }
  }
}
