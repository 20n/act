package act.shared.helpers;

import java.io.Serializable;

public class T<S1, S2, S3> implements Serializable
{
	private static final long serialVersionUID = 1L;
	S1 fst; S2 snd; S3 third;
    public S1 fst() { return this.fst; } // set { this.fst = value; } }
    public S2 snd() { return this.snd; }  // set { this.snd = value; } }
    public S3 third() { return this.third; }  // set { this.snd = value; } }
    
    public T(){}
    
    public T(S1 fst, S2 snd, S3 third)
    {
        this.fst = fst;
        this.snd = snd;
        this.third = third;
    }
	@Override
	public String toString() {
		return "(" + fst + "," + snd + "," + third + ")";
	}
	@Override
	public boolean equals(Object o) {
		if (!(o instanceof T<?,?,?>)) return false;
		T<S1, S2, S3> t = (T<S1, S2, S3>)o;
		return t.fst.equals(this.fst) && t.snd.equals(this.snd) && t.third.equals(this.third);
	}
	@Override
	public int hashCode() {
		return this.fst.hashCode() ^ this.snd.hashCode() ^ this.third.hashCode();
	}
}