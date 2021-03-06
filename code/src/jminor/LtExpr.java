package jminor;

import java.util.List;

import bgu.cs.util.treeGrammar.Node;
import bgu.cs.util.treeGrammar.Visitor;

/**
 * The operator corresponding to a less-than comparison between two values.
 * 
 * @author romanm
 */
public class LtExpr extends BoolExpr {
	public Node getLhs() {
		return args.get(0);
	}

	public Node getRhs() {
		return args.get(1);
	}

	@Override
	public void accept(Visitor v) {
		JminorVisitor whileVisitor = (JminorVisitor) v;
		whileVisitor.visit(this);
	}

	/**
	 * Constructs the right-hand side of an equality comparison.
	 */
	public LtExpr(Node lhs, Node rhs) {
		super(lhs, rhs);
	}

	protected LtExpr(List<Node> args) {
		super(args);
		assertNumOfArgs(2);
	}

	@Override
	public LtExpr clone(List<Node> args) {
		assert args.size() == 2;
		return new LtExpr(args);
	}
}