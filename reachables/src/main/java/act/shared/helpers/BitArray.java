package act.shared.helpers;

public class BitArray {
  boolean[] bits;

  public BitArray(int sz, boolean init) {
    this.bits = new boolean[sz];
    for (int i = 0 ; i<sz; i++) this.bits[i] = init;
  }

  public void Set(int index, boolean val) {
    this.bits[index] = val;
  }

  public int length() {
    return this.bits.length;
  }

  public boolean get(int i) {
    return this.bits[i];
  }
}