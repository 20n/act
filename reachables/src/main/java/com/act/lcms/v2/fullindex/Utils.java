package com.act.lcms.v2.fullindex;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Package-private utility methods for de/serialization and byte buffer manipulation.
 */
public class Utils {

  /**
   * Seralize a Serializable object.
   * @param obj The object to serialize.
   * @param <T> The type of the object to serialize.  Note that this is not bound by serializable for convenience.
   * @return The binary representation of that object.
   * @throws IOException
   */
  static <T> byte[] serializeObject(T obj) throws IOException {
    try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
         ObjectOutputStream oo = new ObjectOutputStream(bos)) {
      oo.writeObject(obj);
      oo.flush();
      return bos.toByteArray();
    }
  }

  /**
   * Deserialize an object that had been serialized via {@link #serializeObject(Object)}.
   * @param bytes The binary representation of an object.
   * @param <T> The type of the object to deserialize.
   * @return The deserialized object.
   * @throws IOException
   * @throws ClassNotFoundException
   */
  static <T> T deserializeObject(byte[] bytes) throws IOException, ClassNotFoundException {
    try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
      // Assumes you know what you're getting into when deserializing.  Don't use this blindly.
      return (T) ois.readObject();
    }
  }

  /* ----------------------------------------
   * Byte buffer utilities.
   * These are all package private so that sibling classes can read from the DB.
   * */

  /**
   * Ensure that a buffer is prepared for reading from the beginning.
   * @param b The buffer to check.
   */
  static void assertReadyForFreshRead(ByteBuffer b) {
    assert(b.limit() != 0); // A zero limit would cause an immediate buffer underflow.
    assert(b.position() == 0); // A non-zero position means either the buffer hasn't been flipped or has been read from.
  }

  /**
   * Convert a list of floating point numbers to a (compact) array of bytes.  Note that this does not use object
   * output streams, but writes the floats' binary representations directly.
   * @param vals The values to serialize as raw bytes.
   * @return A byte array containing the floats in binary form.
   */
  static byte[] floatListToByteArray(List<Float> vals) {
    ByteBuffer buffer = ByteBuffer.allocate(vals.size() * Float.BYTES); // Don't waste any bits!
    vals.forEach(buffer::putFloat);
    buffer.flip(); // Prep for reading.
    return toCompactArray(buffer); // Compact just to be safe, check invariants.
  }

  /**
   * Convert an array of float bytes from {@link #floatListToByteArray(List)} back to a list of floats.
   * @param bytes The bytes to read.
   * @return A list of Float objects constructed from those bytes.
   */
  static List<Float> byteArrayToFloatList(byte[] bytes) {
    ByteBuffer buffer = ByteBuffer.wrap(bytes);
    List<Float> vals = new ArrayList<>(bytes.length / Float.BYTES);
    while (buffer.hasRemaining()) {
      vals.add(buffer.getFloat());
    }
    return vals;
  }

  /**
   * Return an appropriately sized byte array filled with the contents of src.
   *
   * The {@link ByteBuffer#array()} call returns the array that backs the buffer, which is sometimes larger than the
   * data contained in the buffer.  For instance, in this example:
   * <pre>
   * {@code
   *   ByteBuffer buffer = ByteBuffer.allocate(16);
   *   buffer.put("hello world!".getBytes());
   *   byte[] arr = buffer.array();
   * }
   * </pre>
   * {@code arr} will always have length 16, even though we've only written 12 bytes to it.
   *
   * @param src
   * @return
   */
  static byte[] toCompactArray(ByteBuffer src) {
    // Assume src is pre-flipped to avoid a double-flip.
    if (src.hasArray() && src.limit() >= src.capacity()) { // No need to compact if limit covers the full backing array.
      return src.array();
    }
    assertReadyForFreshRead(src);

    // Otherwise, we need to allocate and copy into a new array of the correct size.

    // TODO: is Arrays.copyOf any faster than this?  Ideally we want CoW semantics here...
    // Warning: do not use src.compact().  That does something entirely different.

    /* We use a byte array here rather than another byte buffer for two reasons:
     * 1) It's possible for byte buffers to reside off-heap (like in shared pages), and we definitely want the array on
     *    the heap.
     * 2) If the byte-buffer resides on the heap, it's allocation will be identical, and we save the object management
     *    overhead by not creating another ByteBuffer. */
    byte[] dest = new byte[src.limit()];
    src.get(dest); // Copy the contents of the buffer into our appropriately sized array.
    assert(src.position() == src.limit()); // All bytes should have been read.
    src.rewind(); // Be kind: rewind.
    return dest;
  }

  /**
   * Appends the contents of toAppend to dest if there is capacity, or does the equivalent of C's "realloc" by
   * allocating a larger buffer and copying both dest and toAppend into that new buffer.
   *
   * Always call this function as:
   * <pre>
   * {@code
   *   dest = appendOrRealloc(dest, toAppend)
   * }
   * </pre>
   * as we can't actually resize or reallocate dest once it's been allocated.
   *
   * @param dest The buffer into which to write or to reallocate if necessary.
   * @param toAppend The data to write into dest.
   * @return dest or a new, larger buffer containing both dest and toAppend.
   */
  static ByteBuffer appendOrRealloc(ByteBuffer dest, ByteBuffer toAppend) {
    /* Before reading this function, review this excellent explanation of the behaviors of ByteBuffers:
     * http://mindprod.com/jgloss/bytebuffer.html */

    // Assume toAppend is pre-flipped to avoid a double flip, which would be Very Bad.
    if (dest.capacity() - dest.position() < toAppend.limit()) {
      // Whoops, we have to reallocate!

      ByteBuffer newDest = ByteBuffer.allocate(dest.capacity() << 1); // TODO: make sure these are page-divisible sizes.
      dest.flip(); // Switch dest to reading mode.
      newDest.put(dest); // Write all of dest into the new buffer.
      dest = newDest;
    }
    dest.put(toAppend);
    return dest;
  }
}
