package advantech;

import java.util.HashMap;
import java.util.Map;

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
import org.dsa.iot.dslink.util.handler.Handler;
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
	final Map<String, AdvantechPort> portList = new HashMap<String, AdvantechPort>();
	boolean loaded = false;
	
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
				JsonObject details = (JsonObject) new JsonObject(response).get("Node");
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
				JsonArray ports = (JsonArray) new JsonObject(response).get("Ports");
				for (Object o: ports) {
					AdvantechPort ap = new AdvantechPort(this, (JsonObject) o);
					portList.put(ap.node.getAttribute("PortNumber").getNumber().toString(), ap);
					//ap.init();
				}
			}
		} catch (ApiException e) {
			// TODO Auto-generated catch block
			LOGGER.debug("", e);
		}
		loaded = true;
		
		Action act = new Action(Permission.READ, new AlarmLogHandler());
		act.addParameter(new Parameter("Start", ValueType.NUMBER));
		act.addParameter(new Parameter("Count", ValueType.NUMBER));
		act.addResult(new Parameter("Time", ValueType.STRING));
		act.addResult(new Parameter("Priority", ValueType.STRING));
		act.addResult(new Parameter("TagName", ValueType.STRING));
		act.addResult(new Parameter("Description", ValueType.STRING));
		act.addResult(new Parameter("Action", ValueType.STRING));
		act.setResultType(ResultType.TABLE);
		node.createChild("get alarm log").setAction(act).build();
		
		act = new Action(Permission.READ, new ActionLogHandler());
		act.addParameter(new Parameter("Start", ValueType.NUMBER));
		act.addParameter(new Parameter("Count", ValueType.NUMBER));
		act.addResult(new Parameter("Time", ValueType.STRING));
		act.addResult(new Parameter("Priority", ValueType.STRING));
		act.addResult(new Parameter("TagName", ValueType.STRING));
		act.addResult(new Parameter("Description", ValueType.STRING));
		act.addResult(new Parameter("Action", ValueType.STRING));
		act.setResultType(ResultType.TABLE);
		node.createChild("get action log").setAction(act).build();
	}
	
	private class AlarmLogHandler implements Handler<ActionResult> {
		public void handle(ActionResult event) {
			Map<String, String> pars = new HashMap<String, String>();
			pars.put("HostIp", project.conn.node.getAttribute("IP").getString());
			pars.put("ProjectName", project.name);
			pars.put("NodeName", name);
			pars.put("Start", event.getParameter("Start", ValueType.NUMBER).getNumber().toString());
			pars.put("Count", event.getParameter("Count", ValueType.NUMBER).getNumber().toString());
			
			try {
				String response = Utils.sendGet(Utils.ALARM_LOG, pars, project.conn.auth);
				if (response != null) {
					JsonArray alarmLogs = new JsonObject(response).get("AlarmLogs");
					Table table = event.getTable();
					table.setMode(Mode.APPEND);
					for (Object o: alarmLogs) {
						JsonObject aLog = (JsonObject) o;
						Value time = new Value((String)aLog.get("Time"));
						Value priority = new Value((String)aLog.get("Priority"));
						Value tagname = new Value((String)aLog.get("TagName"));
						Value descr = new Value((String)aLog.get("Description"));
						Value action = new Value((String)aLog.get("Action"));
						Row row = Row.make(time, priority, tagname, descr, action);
						table.addRow(row);
					}
				}
			} catch (ApiException e) {
				LOGGER.debug("", e);
			}
		}
	}
	
	private class ActionLogHandler implements Handler<ActionResult> {
		public void handle(ActionResult event) {
			Map<String, String> pars = new HashMap<String, String>();
			pars.put("HostIp", project.conn.node.getAttribute("IP").getString());
			pars.put("ProjectName", project.name);
			pars.put("NodeName", name);
			pars.put("Start", event.getParameter("Start", ValueType.NUMBER).getNumber().toString());
			pars.put("Count", event.getParameter("Count", ValueType.NUMBER).getNumber().toString());
			
			try {
				String response = Utils.sendGet(Utils.ACTION_LOG, pars, project.conn.auth);
				if (response != null) {
					JsonArray actLogs = new JsonObject(response).get("ActionLogs");
					Table table = event.getTable();
					table.setMode(Mode.APPEND);
					for (Object o: actLogs) {
						JsonObject aLog = (JsonObject) o;
						Value time = new Value((String)aLog.get("Time"));
						Value priority = new Value((String)aLog.get("Priority"));
						Value tagname = new Value((String)aLog.get("TagName"));
						Value descr = new Value((String)aLog.get("Description"));
						Value action = new Value((String)aLog.get("Action"));
						Row row = Row.make(time, priority, tagname, descr, action);
						table.addRow(row);
					}
				}
			} catch (ApiException e) {
				LOGGER.debug("", e);
			}
		}
	}
	
}
