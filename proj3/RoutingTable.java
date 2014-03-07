import java.util.HashMap;
import java.util.Map;

public class RoutingTable {
	Map<RouterCircuit, RouterCircuit> table;

	public RoutingTable() {
		table = new HashMap<RouterCircuit, RouterCircuit>();
	}

	public void addRoute(RouterCircuit start, RouterCircuit end){
		// There might already be an entry from src to (-1,-1), we are overwriting regardless
		// this happens when an endpoint extends the circuit
		table.put(start, end);
	}

	public RouterCircuit getDest(RouterCircuit src) {
		return table.containsKey(src) ? table.get(src) : null;
	}
}
