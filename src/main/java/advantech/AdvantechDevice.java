package advantech;

import java.util.HashMap;
import java.util.Map;

import org.dsa.iot.dslink.node.Node;
import org.dsa.iot.dslink.node.value.Value;
import org.dsa.iot.dslink.util.handler.Handler;
import org.dsa.iot.dslink.util.json.Json;
import org.dsa.iot.dslink.util.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import advantech.Utils.ApiException;

public class AdvantechDevice {
	private final static Logger LOGGER;
	static {
        LOGGER = LoggerFactory.getLogger(AdvantechDevice.class);
    }
	
	AdvantechPort port;
	String name;
	Node node;
	
	AdvantechDevice(AdvantechPort port, JsonObject json) {
		this.port = port;
		this.name = (String) json.get("DeviceName");
		this.node = port.node.createChild(name).build();
		node.setAttribute("PortNumber", new Value((Number) json.get("PortNumber")));
		node.setAttribute("Description", new Value((String) json.get("Description")));
		node.setAttribute("UnitNumber", new Value((Number) json.get("UnitNumber")));
		
		node.getListener().setOnListHandler(new Handler<Node>() {
			private boolean done = false;
			public void handle(Node event) {
				if (done) return;
				done = true;
				init();
			}
		});
	}
	
	void init() {
		Map<String, String> pars = new HashMap<String, String>();
		pars.put("HostIp", port.scada.project.conn.node.getAttribute("IP").getString());
		pars.put("ProjectName", port.scada.project.name);
		pars.put("NodeName", port.scada.name);
		pars.put("Comport", port.node.getAttribute("PortNumber").getNumber().toString());
		pars.put("DeviceName", name);
		try {
			String response = Utils.sendGet(Utils.DEVICE_DETAIL, pars, port.scada.project.conn.auth);
			if (response != null) {
				JsonObject details = (JsonObject) Json.decodeMap(response).get("Device");
				node.setAttribute("PortNumber", new Value((Number) details.get("PortNumber")));
				node.setAttribute("Description", new Value((String) details.get("Description")));
				node.setAttribute("UnitNumber", new Value((Number) details.get("UnitNumber")));
				node.setAttribute("DeviceType", new Value((String) details.get("DeviceType")));
				JsonObject primary = details.get("Primary");
				if (primary != null) {
					node.setAttribute("PrimaryIPAddress", new Value((String) primary.get("IPAddress")));
					node.setAttribute("PrimaryPortNumber", new Value((String) primary.get("PortNumber")));
					node.setAttribute("PrimaryDeviceAddress", new Value((String) primary.get("DeviceAddress")));
				}
				JsonObject secondary = details.get("Secondary");
				if (secondary != null) {
					node.setAttribute("SecondaryIPAddress", new Value((String) secondary.get("IPAddress")));
					node.setAttribute("SecondaryPortNumber", new Value((String) secondary.get("PortNumber")));
					node.setAttribute("SecondaryDeviceAddress", new Value((String) secondary.get("DeviceAddress")));
				}
			}
		} catch (ApiException e) {
			// TODO Auto-generated catch block
			LOGGER.debug("", e);
		}
		
//		try {
//			String response = Utils.sendGet(Utils.DEVICE_TAG_LIST, pars, port.scada.project.conn.auth);
//			if (response != null) {
//				JsonArray tags = (JsonArray) Json.decodeMap(response).get("Tags");
//				JsonArray tagRequestList = new JsonArray();
//				for (Object o: tags) {
//					JsonObject tagReq = new JsonObject();
//					tagReq.put("Name", ((JsonObject) o).get("Name"));
//					tagReq.put("Attributes", new JsonArray("[{\"Name\":\"ALL\"}]"));
//					tagRequestList.add(tagReq);
//				}
//				JsonObject req = new JsonObject();
//				req.put("Tags", tagRequestList);
//				response = Utils.sendPost(Utils.NODE_TAG_DETAIL, pars, port.scada.project.conn.auth, req.toString());
//				JsonArray tagDetail = (JsonArray) Json.decodeMap(response).get("Tags");
//				for (Object o: tagDetail) {
//					AdvantechTag at = new AdvantechTag(this, (JsonObject) o);
//					at.init();
//				}
//			}
//		} catch (ApiException e1) {
//			// TODO Auto-generated catch block
//			LOGGER.debug("", e1);
//		}
	}

}
