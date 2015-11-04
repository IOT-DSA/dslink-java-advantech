package advantech;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.dsa.iot.dslink.node.Node;
import org.dsa.iot.dslink.node.Writable;
import org.dsa.iot.dslink.node.value.Value;
import org.dsa.iot.dslink.node.value.ValuePair;
import org.dsa.iot.dslink.node.value.ValueType;
import org.dsa.iot.dslink.util.handler.Handler;
import org.dsa.iot.dslink.util.json.JsonArray;
import org.dsa.iot.dslink.util.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import advantech.Utils.ApiException;

public class AdvantechTag {
	private final static Logger LOGGER;
	static {
        LOGGER = LoggerFactory.getLogger(AdvantechTag.class);
    }

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
		node.getListener().setValueHandler(new SetHandler());
		node.setWritable(Writable.WRITE);
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
	
	private class SetHandler implements Handler<ValuePair> {
		public void handle(ValuePair event) {
			if (!event.isFromExternalSource()) return;
			
			Map<String, String> pars = new HashMap<String, String>();
			pars.put("ProjectName", device.port.scada.project.name);
			pars.put("HostIp", device.port.scada.project.conn.node.getAttribute("IP").getString());
			
			JsonObject json = new JsonObject();
			JsonArray jarr = new JsonArray();
			JsonObject valobj = new JsonObject();
			valobj.put("Name", name);
			String type = node.getAttribute("TYPE").getString();
			if (isAnalog(type)) {
				valobj.put("Value", event.getCurrent().getNumber());
				jarr.add(valobj);
				json.put("Tags", jarr);
				try {
					Utils.sendPost(Utils.SET_TAG_VALUE, pars, device.port.scada.project.conn.auth, json.toString());
				} catch (ApiException e) {
					// TODO Auto-generated catch block
					LOGGER.debug("", e);
				}
			} else if (isDiscrete(type)) {
				int val = indexOfState(event.getCurrent().getString());
				if (val < 0) {
					LOGGER.debug("Invalid state for discrete value");
					return;
				}
				valobj.put("Value", val);
				jarr.add(valobj);
				json.put("Tags", jarr);
				try {
					Utils.sendPost(Utils.SET_TAG_VALUE, pars, device.port.scada.project.conn.auth, json.toString());
				} catch (ApiException e) {
					// TODO Auto-generated catch block
					LOGGER.debug("", e);
				}
			} else {
				valobj.put("Value", event.getCurrent().getString());
				jarr.add(valobj);
				json.put("Tags", jarr);
				try {
					Utils.sendPost(Utils.SET_TAG_VAUE_TEXT, pars, device.port.scada.project.conn.auth, json.toString());
				} catch (ApiException e) {
					// TODO Auto-generated catch block
					LOGGER.debug("", e);
				}
			}
		}
	}
	
	private int indexOfState(String state) {
		for (int i=0; i<states.length; i++) {
			if (state.equals(states[i])) return i;
		}
		return -1;
	}
	
}
