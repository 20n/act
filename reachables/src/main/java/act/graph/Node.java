package act.graph;

public class Node<N>
{
	public int id;
	public N atom;
	public Node(int id, N t)
	{
		this.id = id;
		this.atom = t;
	}
	@Override
	public String toString()
	{
		// return (atom == N.getDefault() ? "*" : atom.toString()) + "{" + id + "}";
		return atom.toString() + "{" + id + "}";
	}
}
