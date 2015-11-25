package advantech;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

import org.dsa.iot.dslink.node.Node;
import org.dsa.iot.dslink.node.Permission;
import org.dsa.iot.dslink.node.actions.Action;
import org.dsa.iot.dslink.node.actions.ActionResult;
import org.dsa.iot.dslink.node.actions.EditorType;
import org.dsa.iot.dslink.node.actions.Parameter;
import org.dsa.iot.dslink.node.value.Value;
import org.dsa.iot.dslink.node.value.ValueType;
import org.dsa.iot.dslink.util.handler.Handler;
import org.dsa.iot.dslink.util.json.JsonArray;
import org.dsa.iot.dslink.util.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import advantech.Utils.ApiException;

public class AdvantechConn {
	private final static Logger LOGGER;
	static {
        LOGGER = LoggerFactory.getLogger(AdvantechConn.class);
    }

	AdvantechLink link;
	Node node;
	String auth;
	TimeZone timezone;
	
	final private Node statNode;
	boolean loggedIn = false;
	
	AdvantechConn(AdvantechLink adv, Node n) {
		this.link = adv;
		this.node = n;
		this.statNode = node.createChild("STATUS").setValueType(ValueType.STRING).setValue(new Value("Not logged in")).build();
		this.statNode.setSerializable(false);
	}
	
	void init() {
		
		setBasicActions();
		
		login();
		
		setTimeZone();
		
	}
	
	private void setTimeZone() {
		Map<String, String> pars = new HashMap<String, String>();
		pars.put("HostIp", node.getAttribute("IP").getString());
		try {
			String response = Utils.sendGet(Utils.SERVER_TIME, pars, auth);
			String offset =  (String) new JsonObject(response).get("Offset");
			if (!offset.startsWith("-") && !offset.startsWith("+")) offset = "+" + offset;
			String[] offsplit = offset.split(":");
			offset = "GMT" + offsplit[0] + ":" + offsplit[1];
			timezone = TimeZone.getTimeZone(offset);
		} catch (ApiException e) {
			// TODO Auto-generated catch block
			LOGGER.debug("", e);
		}
		
	}

	private void setBasicActions() {
		Action act = new Action(Permission.READ, new RemoveHandler());
		Node anode = node.getChild("remove");
        if (anode == null) node.createChild("remove").setAction(act).build().setSerializable(false);
        else anode.setAction(act);

        act = new Action(Permission.READ, new LoginHandler());
		act.addParameter(new Parameter("IP", ValueType.STRING, node.getAttribute("IP")));
		act.addParameter(new Parameter("Username", ValueType.STRING, node.getAttribute("Username")));
		act.addParameter(new Parameter("Password", ValueType.STRING, node.getAttribute("Password")).setEditorType(EditorType.PASSWORD));
		act.addParameter(new Parameter("Polling interval", ValueType.NUMBER, node.getAttribute("Polling interval")));
		anode = node.getChild("edit");
		if (anode == null) node.createChild("edit").setAction(act).build().setSerializable(false);
		else anode.setAction(act);
		
	}

	void login() {
		statNode.setValue(new Value("Logging in"));
		clear();
		auth = Utils.encodeAuth(node.getAttribute("Username").getString(), node.getAttribute("Password").getString());
		Map<String, String> pars = new HashMap<String, String>();
		pars.put("HostIp", node.getAttribute("IP").getString());
		try {
			String response = Utils.sendGet(Utils.LOGON, pars, auth);
			if (response != null) {
				loggedIn = true;
				statNode.setValue(new Value("Logged in"));
				JsonArray projs = (JsonArray) new JsonObject(response).get("Projects");
				for (Object o: projs) {
					new AdvantechProject(this, (JsonObject) o);
					//ap.init();
				}
			} else {
				loggedIn = false;
				statNode.setValue(new Value("Not logged in"));
			}
		} catch (ApiException e) {
			LOGGER.debug("", e);
			loggedIn = false;
			statNode.setValue(new Value("Not logged in"));
		}
	}
	
	private void clear() {
		if (node.getChildren() == null) return;
		for (Node child: node.getChildren().values()) {
			if (!(child == statNode) && child.getAction() == null) node.removeChild(child);
		}
		
	}

	private class LoginHandler implements Handler<ActionResult> {
		public void handle(ActionResult event) {
			String ip = event.getParameter("IP", ValueType.STRING).getString();
			String user = event.getParameter("Username", ValueType.STRING).getString();
			String pass = event.getParameter("Password", new Value("")).getString();
			double interv = event.getParameter("Polling interval", ValueType.NUMBER).getNumber().doubleValue();
			
			if (new Value(ip).equals(node.getAttribute("IP")) && 
			new Value(user).equals(node.getAttribute("Username")) &&
			new Value(pass).equals(node.getAttribute("Password"))) {
				
				node.setAttribute("Polling interval", new Value(interv));
				setBasicActions();
				Set<AdvantechProject> projs = new HashSet<AdvantechProject>(link.futures.keySet());
				for (AdvantechProject proj: projs) {
					proj.stopPoll();
					if (!proj.subscribed.isEmpty()) proj.startPoll();
				}
				
			} else {
			
				node.setAttribute("IP", new Value(ip));
				node.setAttribute("Username", new Value(user));
				node.setAttribute("Password", new Value(pass));
				node.setAttribute("Polling interval", new Value(interv));
				
				init();
			}
		}
	}
	
	
	private class RemoveHandler implements Handler<ActionResult> {
		public void handle(ActionResult event) {
			remove();
		}
	}

	private void remove() {
		node.clearChildren();
		node.getParent().removeChild(node);
	}

}
