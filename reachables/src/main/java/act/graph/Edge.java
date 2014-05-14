package act.graph;

public class Edge<N,E>
{
	public Node<N> src;
	public Node<N> dst;
	public E bond;
	public Edge(Node<N> n1, Node<N> n2, E t)
	{
		this.src = n1;
		this.dst = n2;
		this.bond = t;
	}
	@Override
	public String toString()
	{
		return this.src + this.bond.toString() + this.dst;
	}
}