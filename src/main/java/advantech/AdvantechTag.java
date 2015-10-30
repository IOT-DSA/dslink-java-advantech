package advantech;

import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Set;

import org.dsa.iot.dslink.node.Node;
import org.dsa.iot.dslink.node.value.Value;
import org.dsa.iot.dslink.node.value.ValueType;
import org.dsa.iot.dslink.util.json.JsonObject;

public class AdvantechTag {

//	AdvantechLevel parent;
	AdvantechDevice device;
	Node node;
	String name;
	
	final String[] states = new String[8];
	
	AdvantechTag(AdvantechDevice device, JsonObject json) {
//		this.parent = parent;
		this.device = device;
		if (json.get("NAME") != null) this.name = (String) json.get("NAME");
		else this.name = (String) json.get("Name");
		ValueType vt;
		String type = json.get("TYPE");
		if (isAnalog(type)) vt = ValueType.NUMBER;
		else if (isDiscrete(type)) {
			Set<String> enums = new HashSet<String>();
			for (int i=0; i<states.length; i++) {
				String s = json.get("DESCR"+i);
				states[i] = s;
				if (s != null) enums.add(s);
			}
			vt = ValueType.makeEnum(enums);
		} else vt = ValueType.STRING;
		this.node = device.node.createChild(name).setValueType(vt).build();
		for (Entry<String, Object> entry: json) {
			if (!entry.getKey().equals("NAME") && !entry.getKey().equals("Name")) {
				node.setAttribute(entry.getKey(), new Value((String) entry.getValue()));
			}
		}
	}
	
	void init() {
		device.port.scada.project.conn.link.setupTag(this);
		if (node.getLink().getSubscriptionManager().hasValueSub(node)) device.port.scada.project.subscribe(this);
	}
	
	static boolean isAnalog(String type) {
		return "ANALOG".equals(type) || "TEMP".equals(type) || "TSP".equals(type);
	}
	
	static boolean isDiscrete(String type) {
		return "DIGITAL".equals(type) || "ST".equals(type) || "TA".equals(type);
	}
	
}
