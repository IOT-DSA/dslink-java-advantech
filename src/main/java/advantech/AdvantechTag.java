package advantech;

import java.util.Map.Entry;

import org.dsa.iot.dslink.node.Node;
import org.dsa.iot.dslink.node.value.Value;
import org.dsa.iot.dslink.node.value.ValueType;
import org.dsa.iot.dslink.util.json.JsonObject;

public class AdvantechTag {

//	AdvantechLevel parent;
	AdvantechDevice device;
	Node node;
	String name;
	
	AdvantechTag(AdvantechDevice device, JsonObject json) {
//		this.parent = parent;
		this.device = device;
		if (json.get("NAME") != null) this.name = (String) json.get("NAME");
		else this.name = (String) json.get("Name");
		this.node = device.node.createChild(name).setValueType(ValueType.NUMBER).build();
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
	
}
