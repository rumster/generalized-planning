package heap;

import java.util.ArrayList;
import java.util.List;

import grammar.*;

/**
 * The operator corresponding to an equality comparison between two values.
 * 
 * @author romanm
 */
public class EqExpr extends Node implements Condition {
	protected List<Node> args = new ArrayList<>(2);

	protected EqExpr(int numOfNonterminals) {
		super(numOfNonterminals);
	}

	@Override
	public List<Node> getArgs() {
		return args;
	}

	public Node getLhs() {
		return args.get(0);
	}

	public Node getRhs() {
		return args.get(1);
	}

	@Override
	public void accept(Visitor v) {
		PWhileVisitor whileVisitor = (PWhileVisitor) v;
		whileVisitor.visit(this);
	}

	/**
	 * Constructs the right-hand side of an equality comparison.
	 */
	public EqExpr(Node lhs, Node rhs) {
		super(lhs.numOfNonterminals + rhs.numOfNonterminals);
		args.add(lhs);
		args.add(rhs);
	}

	protected EqExpr(List<Node> args) {
		super(countNonterminals(args));
		assert args.size() == 2 : "Illegal number of arguments for " + getClass().getSimpleName() + ": " + args.size()
				+ "!";
		this.args = args;
	}

	@Override
	public EqExpr clone(List<Node> args) {
		return new EqExpr(args);
	}

	@Override
	public boolean holds(Store s) {
		return PWhileInterpreter.v.test(this, s);
	}
}