package advantech;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.dsa.iot.dslink.node.Node;
import org.dsa.iot.dslink.node.value.Value;
import org.dsa.iot.dslink.util.Objects;
import org.dsa.iot.dslink.util.json.Json;
import org.dsa.iot.dslink.util.json.JsonArray;
import org.dsa.iot.dslink.util.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import advantech.Utils.ApiException;

public class AdvantechProject {
	private final static Logger LOGGER;
	static {
        LOGGER = LoggerFactory.getLogger(AdvantechProject.class);
    }
	
	AdvantechConn conn;
	String name;
	Node node;
	Map<String, AdvantechTag> subscribed = new ConcurrentHashMap<String, AdvantechTag>();
	
	AdvantechProject(AdvantechConn conn, JsonObject json) {
		this.conn = conn;
		this.name = (String) json.get("Name");
		this.node = conn.node.createChild(name).build();
		node.setAttribute("Id", new Value((Number) json.get("Id")));
		node.setAttribute("Description", new Value((String) json.get("Description")));
		
	}
	
	void init() {
		Map<String, String> pars = new HashMap<String, String>();
		pars.put("ProjectName", name);
		pars.put("HostIp", conn.node.getAttribute("IP").getString());
		try {
			String response = Utils.sendGet(Utils.PROJ_DETAIL, pars, conn.auth);
			if (response != null) {
				JsonObject details = (JsonObject) Json.decodeMap(response).get("Project");
				node.setAttribute("Id", new Value((Number) details.get("Id")));
				node.setAttribute("Description", new Value((String) details.get("Description")));
				node.setAttribute("Ip", new Value((String) details.get("Ip")));
				node.setAttribute("Port", new Value((Number) details.get("Port")));
				node.setAttribute("TimeOut", new Value((Number) details.get("TimeOut")));
				node.setAttribute("AccessSecurityCode", new Value((String) details.get("AccessSecurityCode")));
				node.setAttribute("HTTPPort", new Value((Number) details.get("HTTPPort")));
			}
		} catch (ApiException e) {
			// TODO Auto-generated catch block
			LOGGER.debug("", e);
		}
		
//		try {
//			String response = Utils.sendGet(Utils.PROJ_TAG_LIST, pars, conn.auth);
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
//			response = Utils.sendPost(Utils.PROJ_TAG_DETAIL, pars, conn.auth, req.toString());
//			JsonArray tagDetail = (JsonArray) Json.decodeMap(response).get("Tags");
//			for (Object o: tagDetail) {
//				AdvantechTag at = new AdvantechTag(this, (JsonObject) o);
//				at.init();
//			}
//		} catch (ApiException e1) {
//			// TODO Auto-generated catch block
//		}
		
		try {
			String response = Utils.sendGet(Utils.NODE_LIST, pars, conn.auth);
			if (response != null) {
				JsonArray nodes = (JsonArray) Json.decodeMap(response).get("Nodes");
				for (Object o: nodes) {
					AdvantechNode an = new AdvantechNode(this, (JsonObject) o);
					an.init();
				}
			}
		} catch (ApiException e) {
			// TODO Auto-generated catch block
			LOGGER.debug("", e);
		}
	}
	
	void poll() {
		JsonObject json = new JsonObject();
		JsonArray jarr = new JsonArray();
		Map<String, AdvantechTag> subCopy = new HashMap<String, AdvantechTag>(subscribed);
		for (AdvantechTag tag: subCopy.values()) {
			JsonObject tagObj = new JsonObject();
			tagObj.put("Name", tag.name);
			jarr.add(tagObj);
		}
		json.put("Tags", jarr);
		
		Map<String, String> pars = new HashMap<String, String>();
		pars.put("HostIp", conn.node.getAttribute("IP").getString());
		pars.put("ProjectName", name);
		
		try {
			String response = Utils.sendPost(Utils.TAG_VALUE, pars, conn.auth, json.toString());
			if (response == null) return;
			JsonArray vals = (JsonArray) Json.decodeMap(response).get("Values");
			for (Object o: vals) {
				JsonObject jo = (JsonObject) o;
				AdvantechTag tag = subCopy.get((String) jo.get("Name"));
				Object val = jo.get("Value");
				String type = tag.node.getAttribute("TYPE").getString();
				if (AdvantechTag.isAnalog(type)) tag.node.setValue(new Value((Number) val));
				else if (AdvantechTag.isDiscrete(type)) tag.node.setValue(new Value(tag.states[(Integer) val]));
				else tag.node.setValue(new Value((String) val));
			}
		} catch (ApiException e) {
			// TODO Auto-generated catch block
			LOGGER.debug("", e);
		}
	}
	
	void startPoll() {
		ScheduledThreadPoolExecutor stpe = Objects.getDaemonThreadPool();
		long interval = (long) (1000*conn.node.getAttribute("Polling interval").getNumber().doubleValue());
		ScheduledFuture<?> fut = stpe.scheduleWithFixedDelay(new Runnable() {
			public void run() {
				LOGGER.debug("Polling for " + name);
				poll();
			}
		}, 0, interval, TimeUnit.MILLISECONDS);
		conn.link.futures.put(this, fut);
	}
	
	void stopPoll() {
		ScheduledFuture<?> fut = conn.link.futures.remove(this);
		if (fut != null) {
			fut.cancel(false);
		}
	}
	
	void subscribe(AdvantechTag at) {
		subscribed.put(at.name, at);
		if (conn.link.futures.containsKey(this) || subscribed.isEmpty()) return;
		startPoll();
	}
	
	void unsubscribe(AdvantechTag at) {
		subscribed.remove(at.name);
		if (subscribed.isEmpty()) {
			stopPoll();
		}
	}
	
}
