package advantech;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.dsa.iot.dslink.node.Node;
import org.dsa.iot.dslink.node.Permission;
import org.dsa.iot.dslink.node.actions.Action;
import org.dsa.iot.dslink.node.actions.ActionResult;
import org.dsa.iot.dslink.node.actions.Parameter;
import org.dsa.iot.dslink.node.actions.ResultType;
import org.dsa.iot.dslink.node.actions.table.Row;
import org.dsa.iot.dslink.node.actions.table.Table;
import org.dsa.iot.dslink.node.actions.table.Table.Mode;
import org.dsa.iot.dslink.node.value.Value;
import org.dsa.iot.dslink.node.value.ValueType;
import org.dsa.iot.dslink.util.Objects;
import org.dsa.iot.dslink.util.handler.Handler;
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
	final Map<String, AdvantechNode> scadaList = new HashMap<String, AdvantechNode>();
	Map<String, AdvantechTag> subscribed = new ConcurrentHashMap<String, AdvantechTag>();
	
	AdvantechProject(AdvantechConn conn, JsonObject json) {
		this.conn = conn;
		this.name = (String) json.get("Name");
		this.node = conn.node.createChild(name).build();
		node.setAttribute("Id", new Value((Number) json.get("Id")));
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
		pars.put("ProjectName", name);
		pars.put("HostIp", conn.node.getAttribute("IP").getString());
		try {
			String response = Utils.sendGet(Utils.PROJ_DETAIL, pars, conn.auth);
			if (response != null) {
				JsonObject details = (JsonObject) new JsonObject(response).get("Project");
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
		
		try {
			String response = Utils.sendGet(Utils.NODE_LIST, pars, conn.auth);
			if (response != null) {
				JsonArray nodes = (JsonArray) new JsonObject(response).get("Nodes");
				for (Object o: nodes) {
					AdvantechNode an = new AdvantechNode(this, (JsonObject) o);
					scadaList.put(an.name, an);
					//an.init();
				}
			}
		} catch (ApiException e) {
			// TODO Auto-generated catch block
			LOGGER.debug("", e);
		}
		
		JsonObject json = new JsonObject();
		JsonArray jarr = new JsonArray();
		JsonObject filt = new JsonObject();
		filt.put("Name", "BLOCK");
		filt.put("Value", "FALSE");
		jarr.add(filt);
		json.put("Filters", jarr);
		try {
			String response = Utils.sendPost(Utils.PROJ_TAG_LIST, pars, conn.auth, json.toString());
			if (response != null) {
				JsonArray tags = (JsonArray) new JsonObject(response).get("Tags");
				JsonArray tagRequestList = new JsonArray();
				for (Object o: tags) {
					JsonObject tagReq = new JsonObject();
					tagReq.put("Name", ((JsonObject) o).get("Name"));
					tagReq.put("Attributes", new JsonArray("[{\"Name\":\"ALL\"}]"));
					tagRequestList.add(tagReq);
				}
				JsonObject req = new JsonObject();
				req.put("Tags", tagRequestList);
				response = Utils.sendPost(Utils.PROJ_TAG_DETAIL, pars, conn.auth, req.toString());
				JsonArray tagDetail = (JsonArray) new JsonObject(response).get("Tags");
				for (Object o: tagDetail) {
					AdvantechTag at = new AdvantechTag(this, (JsonObject) o);
					at.init();
				}
			}
		} catch (ApiException e1) {
			// TODO Auto-generated catch block
			LOGGER.debug("", e1);
		}
		
		try {
			String response = Utils.sendGet(Utils.PROJ_BLOCK_LIST, pars, conn.auth);
			if (response != null) {
				JsonArray blocks = (JsonArray) new JsonObject(response).get("Blocks");
				for (Object o: blocks) {
					new AdvantechBlock(this, (JsonObject) o);
				}
			}
		} catch (ApiException e1) {
			// TODO Auto-generated catch block
			LOGGER.debug("", e1);
		}
		
		Action act = new Action(Permission.READ, new AlarmSummaryHandler());
		act.addParameter(new Parameter("Start", ValueType.NUMBER));
		act.addParameter(new Parameter("Count", ValueType.NUMBER));
		act.addResult(new Parameter("Name", ValueType.STRING));
		act.addResult(new Parameter("Time", ValueType.STRING));
		act.addResult(new Parameter("AlarmPriority", ValueType.NUMBER));
		act.addResult(new Parameter("AckStatus", ValueType.NUMBER));
		act.addResult(new Parameter("AlarmGroup", ValueType.NUMBER));
		act.addResult(new Parameter("AlarmValue", ValueType.NUMBER));
		act.addResult(new Parameter("AlarmLimit", ValueType.NUMBER));
		act.addResult(new Parameter("AlarmType", ValueType.NUMBER));
		act.addResult(new Parameter("NodeName", ValueType.STRING));
		act.addResult(new Parameter("Locked", ValueType.NUMBER));
		act.setResultType(ResultType.TABLE);
		node.createChild("get alarm summary").setAction(act).build().setSerializable(false);
		
		act = new Action(Permission.READ, new AckAllHandler());
		act.addParameter(new Parameter("IP Address", ValueType.STRING));
		act.addParameter(new Parameter("Computer", ValueType.STRING));
		node.createChild("acknowledge all alarms").setAction(act).build().setSerializable(false);
	}
	
	private class AckAllHandler implements Handler<ActionResult> {
		public void handle(ActionResult event) {
			String ip = event.getParameter("IP Address", ValueType.STRING).getString();
			String comp = event.getParameter("Computer", ValueType.STRING).getString();
			String user = conn.node.getAttribute("Username").getString();
			
			Map<String, String> pars = new HashMap<String, String>();
			pars.put("ProjectName", name);
			pars.put("HostIp", conn.node.getAttribute("IP").getString());
			
			JsonObject json = new JsonObject();
			json.put("IpAddress", ip);
			json.put("Computer", comp);
			json.put("User", user);
			
			try {
				Utils.sendPost(Utils.PROJ_ALARM_ACK_ALL, pars, conn.auth, json.toString());
			} catch (ApiException e) {
				LOGGER.debug("", e);
			}
		}
	}
	
	private class AlarmSummaryHandler implements Handler<ActionResult> {
		public void handle(ActionResult event) {
			Map<String, String> pars = new HashMap<String, String>();
			pars.put("ProjectName", name);
			pars.put("HostIp", conn.node.getAttribute("IP").getString());
			pars.put("Start", event.getParameter("Start", ValueType.NUMBER).getNumber().toString());
			pars.put("Count", event.getParameter("Count", ValueType.NUMBER).getNumber().toString());
			
			try {
				String response = Utils.sendGet(Utils.ALARM_SUMMARY, pars, conn.auth);
				if (response != null) {
					JsonArray alarmTags = new JsonObject(response).get("AlarmTagList");
					Table table = event.getTable();
					table.setMode(Mode.APPEND);
					for (Object o: alarmTags) {
						JsonObject aTag = (JsonObject) o;
						Value name = new Value((String)aTag.get("Name"));
						Value time = new Value((String)aTag.get("Time"));
						Value priority = new Value((Number)aTag.get("AlarmPriority"));
						Value status = new Value((Number)aTag.get("AckStatus"));
						Value group = new Value((Number)aTag.get("AlarmGroup"));
						Value value = new Value((Number)aTag.get("AlarmValue"));
						Value limit = new Value((Number)aTag.get("AlarmLimit"));
						Value type = new Value((Number)aTag.get("AlarmType"));
						Value nodename = new Value((String)aTag.get("NodeName"));
						Value locked = new Value((Number)aTag.get("Locked"));
						Row row = Row.make(name, time, priority, status, group, value, limit, type, nodename, locked);
						table.addRow(row);
					}
				}
			} catch (ApiException e) {
				LOGGER.debug("", e);
			}
		}
	}
	
	void poll() {
		JsonObject json = new JsonObject();
		JsonObject textjson = new JsonObject();
		JsonArray jarr = new JsonArray();
		JsonArray textjarr = new JsonArray();
		Map<String, AdvantechTag> subCopy = new HashMap<String, AdvantechTag>(subscribed);
		for (AdvantechTag tag: subCopy.values()) {
			JsonObject tagObj = new JsonObject();
			tagObj.put("Name", tag.name);
			
			String type = tag.node.getAttribute("TYPE").getString();
			if (AdvantechTag.isAnalog(type) || AdvantechTag.isDiscrete(type)) jarr.add(tagObj);
			else textjarr.add(tagObj);
		}
		json.put("Tags", jarr);
		textjson.put("Tags", textjarr);
		
		Map<String, String> pars = new HashMap<String, String>();
		pars.put("HostIp", conn.node.getAttribute("IP").getString());
		pars.put("ProjectName", name);
		
		try {
			String response = Utils.sendPost(Utils.TAG_VALUE, pars, conn.auth, json.toString());
			if (response != null) {
				JsonArray vals = (JsonArray) new JsonObject(response).get("Values");
				for (Object o: vals) {
					JsonObject jo = (JsonObject) o;
					AdvantechTag tag = subCopy.get((String) jo.get("Name"));
					Object val = jo.get("Value");
					String type = tag.node.getAttribute("TYPE").getString();
					if (AdvantechTag.isAnalog(type)) tag.node.setValue(new Value((Number) val));
					else if (AdvantechTag.isDiscrete(type)) {
						tag.node.setValue(new Value((Integer) val));
						int v = (Integer) val;
						if (v >= 0 && v < tag.states.length) tag.displayNode.setValue(new Value(tag.states[v]));
					}
					else tag.node.setValue(new Value(val.toString()));
				}
			}
		} catch (ApiException e) {
			// TODO Auto-generated catch block
			LOGGER.debug("", e);
		}
		
		try {
			String response = Utils.sendPost(Utils.TAG_VALUE_TEXT, pars, conn.auth, textjson.toString());
			if (response != null) {
				JsonArray vals = (JsonArray) new JsonObject(response).get("Values");
				for (Object o: vals) {
					JsonObject jo = (JsonObject) o;
					AdvantechTag tag = subCopy.get((String) jo.get("Name"));
					Object val = jo.get("Value");
					
					tag.node.setValue(new Value((String) val));
				}
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
