package heap;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import org.stringtemplate.v4.ST;

import bgu.cs.util.FileUtils;
import bgu.cs.util.Pair;
import bgu.cs.util.STGLoader;
import bgu.cs.util.Tuple3;
import bgu.cs.util.graph.HashMultiGraph;
import bgu.cs.util.graph.MultiGraph;
import bgu.cs.util.graph.visualization.GraphizVisualizer;
import gnu.trove.map.TObjectIntMap;
import gnu.trove.map.hash.TObjectIntHashMap;

/**
 * Store-related utility methods.
 * 
 * @author romanm
 */
public class StoreUtils {
	protected static STGLoader templates = new STGLoader(Store.class);

	private static Map<Obj, String> objToName = new HashMap<>();

	private static String getObjName(Obj o) {
		String result = objToName.get(o);
		if (result == null) {
			result = o.type.name + "#" + objToName.size();
			objToName.put(o, result);
		}
		return result;
	}

	public static boolean typecheck(Var var, Val v) {
		return (var instanceof IntVar && v instanceof IntVal)
				|| (var instanceof RefVar && (v == null || v instanceof Obj));
	}

	public static boolean typecheck(Field field, Val v) {
		return (field instanceof IntField && v instanceof IntVal)
				|| (field instanceof RefField && (v == null || v instanceof Obj));
	}

	public static boolean typecheck(Field field, Obj o) {
		return (field.srcType.equals(o.type));
	}

	/**
	 * Renders the given store into an image file with the given base name.
	 */
	public static void printStore(Store state, String filename, Logger logger) {
		String dotStr = storeToDOT(state);
		GraphizVisualizer.renderToFile(dotStr, FileUtils.base(filename), FileUtils.suffix(filename), logger);
	}

	/**
	 * Returns a representation of the given store in the DOT (graph language)
	 * format.
	 */
	public static String storeToDOT(Store state) {
		ST template = templates.load("StoreDOT");

		// Assign objects names and render their non-reference values.
		Map<Obj, String> objToDotNodeName = new HashMap<>();
		objToDotNodeName.put(Obj.NULL, "null");
		int i = 0;
		for (Obj o : state.getObjects()) {
			String objName = getObjName(o);
			String dotNodeName = "N" + i;
			objToDotNodeName.put(o, dotNodeName);
			ST objContent = templates.load("objectContent");
			objContent.add("name", objName);
			for (Field f : o.type.fields) {
				if (state.isInitialized(o, f) && !(f instanceof RefField)) {
					objContent.add("vals", f.name + "=" + state.eval(o, f).toString());
				}
			}
			template.add("objects", new Pair<String, String>(dotNodeName, objContent.render()));
			++i;
		}

		// Assign a node to each variable.
		Map<RefVar, String> refVarToDotNodeName = new HashMap<>();
		for (Var var : state.getEnvMap().keySet()) {
			if (var instanceof RefVar) {
				RefVar refVar = (RefVar) var;
				refVarToDotNodeName.put(refVar, var.name);
				template.add("refVarNodes", var.name);
			} else {
				if (state.isInitialized(var)) {
					template.add("nonRefVarVals", new Pair<String, String>(var.name, var.name + "=" + state.eval(var)));
				}
			}
		}

		// Add arrows from reference variables to objects.
		state.getEnvMap().forEach((var, val) -> {
			if (state.isInitialized(var) && var instanceof RefVar) {
				RefVar refVar = (RefVar) var;
				Obj o = (Obj) val;
				template.add("refVarVals",
						new Pair<String, String>(refVarToDotNodeName.get(refVar), objToDotNodeName.get(o)));
			}
		});

		// Add arrows for reference fields.
		for (Obj src : state.getObjects()) {
			String srcName = objToDotNodeName.get(src);
			for (Field f : src.type.fields) {
				if (state.isInitialized(src, f) && f instanceof RefField) {
					RefField refField = (RefField) f;
					Obj dst = state.eval(src, refField);
					String dstName = objToDotNodeName.get(dst);
					template.add("refFields", new Tuple3<String, String, String>(srcName, dstName, refField.name));
				}
			}
		}

		return template.render();
	}

	/**
	 * Returns a multigraph whose nodes are the objects of the state and the edges
	 * are labeled by the corresponding {@link RefField} fields.
	 */
	public static MultiGraph<Obj, RefField> storeToObjMultiGraph(Store state) {
		HashMultiGraph<Obj, RefField> result = new HashMultiGraph<>();
		result.addNode(Obj.NULL);
		for (Obj o : state.getObjects()) {
			result.addNode(o);
		}

		for (Obj o : state.getObjects()) {
			for (Map.Entry<Field, Val> fieldEdge : state.geFields(o).entrySet()) {
				Field field = fieldEdge.getKey();
				if (field instanceof RefField) {
					RefField refField = (RefField) field;
					Obj succ = (Obj) fieldEdge.getValue();
					result.addEdge(o, succ, refField);
				}
			}
		}
		return result;
	}

	public static boolean reachableObjects(Store state) {
		return reachableObjects(state, new LinkedList<Var>());
	}

	public static boolean reachableObjects(Store state, Collection<Var> excludeSet) {
		int reachableObjects = StoreUtils.search(state, false, excludeSet).size();
		int totalObjects = state.getObjects().size();
		return (reachableObjects == totalObjects);
	}

	public static List<Obj> dfs(Store state) {
		return search(state, true);
	}

	public static List<Obj> bfs(Store state) {
		return search(state, false);

	}

	public static List<Obj> search(Store state, boolean depth) {
		return search(state, depth, new LinkedList<Var>());
	}

	public static List<Obj> search(Store state, boolean depth, Collection<Var> excludeSet) {
		List<Obj> result = new ArrayList<>(state.getObjects().size());
		LinkedList<Obj> open = new LinkedList<>();

		for (Map.Entry<Var, Val> valuation : state.getEnvMap().entrySet()) {
			Var var = valuation.getKey();
			Val v = valuation.getValue();

			if (!excludeSet.contains(var) && v instanceof Obj && v != Obj.NULL) {
				if (depth)
					open.addFirst((Obj) v);
				else
					open.addLast((Obj) v);
			}
		}
		Set<Obj> closed = new HashSet<>();
		while (!open.isEmpty()) {
			Obj o = open.removeFirst();
			if (closed.contains(o) || o == Obj.NULL)
				continue;
			closed.add(o);
			result.add(o);
			for (Field field : o.type.fields) {
				if (field instanceof RefField) {
					RefField refField = (RefField) field;
					Obj succ = state.eval(o, refField);
					if (succ != Obj.NULL && !closed.contains(succ)) {
						if (depth)
							open.addFirst(succ);
						else
							open.addLast(succ);
					}
				}
			}
		}
		return result;
	}

	/**
	 * Returns a map associating each object with its distance from the given set of
	 * source objects.
	 * 
	 * @param state
	 *            A state.
	 * @param sources
	 *            A set of objects in the given state.
	 */
	public static TObjectIntMap<Obj> bfsMap(Store state, Set<Obj> sources) {
		Set<Obj> open = sources;
		TObjectIntHashMap<Obj> result = new TObjectIntHashMap<>(state.objects.size(), 0.7f, -1);
		for (Obj o : sources) {
			result.put(o, 0);
		}
		Set<Obj> closed = new HashSet<>();
		while (!open.isEmpty()) {
			Iterator<Obj> firstIt = open.iterator();
			Obj o = firstIt.next();
			firstIt.remove();
			if (closed.contains(o) || o == Obj.NULL)
				continue;
			closed.add(o);
			int dist = result.get(o);
			for (Field field : o.type.fields) {
				if (field instanceof RefField) {
					RefField refField = (RefField) field;
					Obj succ = state.eval(o, refField);
					int succDist = result.getNoEntryValue();
					if (succDist == -1) {
						result.put(succ, dist + 1);
					} else {
						int minDist = dist < succDist ? dist : succDist;
						result.put(succ, minDist);
					}
					if (succ != Obj.NULL && !closed.contains(succ)) {
						open.add(succ);
					}
				}
			}
		}
		return result;
	}
}