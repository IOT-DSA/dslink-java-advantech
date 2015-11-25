package advantech;

import java.util.HashMap;
import java.util.Map;

import org.dsa.iot.dslink.node.Node;
import org.dsa.iot.dslink.node.value.Value;
import org.dsa.iot.dslink.util.handler.Handler;
import org.dsa.iot.dslink.util.json.JsonArray;
import org.dsa.iot.dslink.util.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import advantech.Utils.ApiException;

public class AdvantechPort {
	private final static Logger LOGGER;
	static {
        LOGGER = LoggerFactory.getLogger(AdvantechPort.class);
    }
	
	AdvantechNode scada;
	Node node;
	final Map<String, AdvantechDevice> deviceList = new HashMap<String, AdvantechDevice>();
	boolean loaded = false;
	
	AdvantechPort(AdvantechNode scada, JsonObject json) {
		this.scada = scada;
		String name = (String) json.get("InterfaceName") + (Number) json.get("PortNumber");
		this.node = scada.node.createChild(name).build();
		node.setAttribute("InterfaceName", new Value((String) json.get("InterfaceName")));
		node.setAttribute("PortNumber", new Value((Number) json.get("PortNumber")));
		node.setAttribute("Description", new Value((String) json.get("Description")));
		
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
		pars.put("HostIp", scada.project.conn.node.getAttribute("IP").getString());
		pars.put("ProjectName", scada.project.name);
		pars.put("NodeName", scada.name);
		pars.put("Comport", node.getAttribute("PortNumber").getNumber().toString());
		try {
			String response = Utils.sendGet(Utils.PORT_DETAIL, pars, scada.project.conn.auth);
			if (response != null) {
				JsonObject details = (JsonObject) new JsonObject(response).get("Port");
				node.setAttribute("InterfaceName", new Value((String) details.get("InterfaceName")));
				node.setAttribute("ComportNbr", new Value((Number) details.get("ComportNbr")));
				node.setAttribute("Description", new Value((String) details.get("Description")));
				node.setAttribute("BaudRate", new Value((Number) details.get("BaudRate")));
				node.setAttribute("DataBit", new Value((Number) details.get("DataBit")));
				node.setAttribute("StopBit", new Value((Number) details.get("StopBit")));
				node.setAttribute("Parity", new Value((Number) details.get("Parity")));
				node.setAttribute("ScanTime", new Value((Number) details.get("ScanTime")));
				node.setAttribute("TimeOut", new Value((Number) details.get("TimeOut")));
				node.setAttribute("RetryCount", new Value((Number) details.get("RetryCount")));
				node.setAttribute("AutoRecoverTime", new Value((Number) details.get("AutoRecoverTime")));
				node.setAttribute("OPCServer", new Value((String) details.get("OPCServer")));
				node.setAttribute("OPCServerType", new Value((String) details.get("OPCServerType")));
			}
		} catch (ApiException e) {
			// TODO Auto-generated catch block
			LOGGER.debug("", e);
		}
		
//		try {
//			String response = Utils.sendGet(Utils.PORT_TAG_LIST, pars, scada.project.conn.auth);
//			JsonArray tags = (JsonArray) Json.decodeMap(response).get("Tags");
//			for (Object o: tags) {
//				AdvantechTag at = new AdvantechTag(this, (JsonObject) o);
//				at.init();
//			}
//		} catch (ApiException e1) {
//			// TODO Auto-generated catch block
//		}
//		
		try {
			String response = Utils.sendGet(Utils.DEVICE_LIST, pars, scada.project.conn.auth);
			if (response != null) {
				JsonArray devs = (JsonArray) new JsonObject(response).get("Devices");
				for (Object o: devs) {
					AdvantechDevice ad = new AdvantechDevice(this, (JsonObject) o);
					deviceList.put(ad.name, ad);
					//ad.init();
				}
			}
		} catch (ApiException e) {
			// TODO Auto-generated catch block
			LOGGER.debug("", e);
		}
		
		loaded = true;
		
	}

}
