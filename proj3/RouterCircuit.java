/**
 * This tuple represents a hop from router with id "routerNum" to another router along circuit id "circuitId"
 * These will be used in the routing table.
 */
public class RouterCircuit {
	int routerNum;
	int circuitId;

	public RouterCircuit(int routerNum, int circuitId) {
		this.routerNum = routerNum;
		this.circuitId = circuitId;
	}

	@Override
	public int hashCode() {
		return routerNum * 17 + circuitId;
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
		return (this.routerNum == rc.routerNum && this.circuitId == rc.circuitId);

	}
}
