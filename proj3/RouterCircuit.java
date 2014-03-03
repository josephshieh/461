/**
 * This tuple represents a hop from router with id "routerNum" to another router along circuit id "circuitId"
 * These will be used in the routing table.
 */
public class RouterCircuit {
	long agentId;
	int circuitId;

	public RouterCircuit(long routerNum, int circuitId) {
		this.agentId = routerNum;
		this.circuitId = circuitId;
	}

	@Override
	public int hashCode() {
		return (agentId + "").hashCode() * 17 + circuitId;
	}

	@Override
	public boolean equals(Object o){
		if (this == o) {
			return true;
		}
		if(!(o instanceof RouterCircuit)) {
			return false;
		}
		RouterCircuit rc = (RouterCircuit) o;
		return (this.agentId == rc.agentId && this.circuitId == rc.circuitId);

	}
}
