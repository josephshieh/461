import java.util.HashMap;
import java.util.Map;

public class RoutingTable {
	Map<RouterCircuit, RouterCircuit> table;

	public RoutingTable() {
		table = new HashMap<RouterCircuit, RouterCircuit>();
	}

	public void addRoute(RouterCircuit start, RouterCircuit end){
		if(!table.containsKey(start)) {
			table.put(start, end);
		}
	}
}
