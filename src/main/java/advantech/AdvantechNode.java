package advantech;

import java.util.HashMap;
import java.util.Map;

import org.dsa.iot.dslink.node.Node;
import org.dsa.iot.dslink.node.value.Value;
import org.dsa.iot.dslink.util.handler.Handler;
import org.dsa.iot.dslink.util.json.Json;
import org.dsa.iot.dslink.util.json.JsonArray;
import org.dsa.iot.dslink.util.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import advantech.Utils.ApiException;

public class AdvantechNode {
	private final static Logger LOGGER;
	static {
        LOGGER = LoggerFactory.getLogger(AdvantechNode.class);
    }

	AdvantechProject project;
	String name;
	Node node;
	
	AdvantechNode(AdvantechProject proj, JsonObject json) {
		this.project = proj;
		this.name = (String) json.get("NodeName");
		this.node = project.node.createChild(name).build();
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
		pars.put("HostIp", project.conn.node.getAttribute("IP").getString());
		pars.put("ProjectName", project.name);
		pars.put("NodeName", name);
		try {
			String response = Utils.sendGet(Utils.NODE_DETAIL, pars, project.conn.auth);
			if (response != null) {
				JsonObject details = (JsonObject) Json.decodeMap(response).get("Node");
				node.setAttribute("ProjectId", new Value((Number) details.get("ProjectId")));
				node.setAttribute("NodeId", new Value((Number) details.get("NodeId")));
				node.setAttribute("Description", new Value((String) details.get("Description")));
				node.setAttribute("Address", new Value((String) details.get("Address")));
				node.setAttribute("Port1", new Value((Number) details.get("Port1")));
				node.setAttribute("Port2", new Value((Number) details.get("Port2")));
				node.setAttribute("Timeout", new Value((Number) details.get("Timeout")));
			}
		} catch (ApiException e) {
			// TODO Auto-generated catch block
			LOGGER.debug("", e);
		}
		
//		try {
//			String response = Utils.sendGet(Utils.NODE_TAG_LIST, pars, project.conn.auth);
//			JsonArray tags = (JsonArray) Json.decodeMap(response).get("Tags");
//			JsonArray tagRequestList = new JsonArray();
//			for (Object o: tags) {
//				JsonObject tagReq = new JsonObject();
//				tagReq.put("Name", ((JsonObject) o).get("Name"));
//				tagReq.put("Attributes", new JsonArray("[{\"Name\":\"ALL\"}]"));
//				tagRequestList.add(tagReq);
//			}
//			JsonObject req = new JsonObject();
//			req.put("Tags", tagRequestList);
//			response = Utils.sendPost(Utils.NODE_TAG_DETAIL, pars, project.conn.auth, req.toString());
//			JsonArray tagDetail = (JsonArray) Json.decodeMap(response).get("Tags");
//			for (Object o: tagDetail) {
//				AdvantechTag at = new AdvantechTag(this, (JsonObject) o);
//				at.init();
//			}
//		} catch (ApiException e1) {
//			// TODO Auto-generated catch block
//		}
		
		try {
			String response = Utils.sendGet(Utils.PORT_LIST, pars, project.conn.auth);
			if (response != null) {
				JsonArray ports = (JsonArray) Json.decodeMap(response).get("Ports");
				for (Object o: ports) {
					AdvantechPort ap = new AdvantechPort(this, (JsonObject) o);
					//ap.init();
				}
			}
		} catch (ApiException e) {
			// TODO Auto-generated catch block
			LOGGER.debug("", e);
		}
	}
	
}
