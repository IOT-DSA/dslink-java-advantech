package advantech;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import org.dsa.iot.dslink.node.Node;
import org.dsa.iot.dslink.node.Permission;
import org.dsa.iot.dslink.node.actions.Action;
import org.dsa.iot.dslink.node.actions.ActionResult;
import org.dsa.iot.dslink.node.actions.EditorType;
import org.dsa.iot.dslink.node.actions.Parameter;
import org.dsa.iot.dslink.node.value.Value;
import org.dsa.iot.dslink.node.value.ValueType;
import org.dsa.iot.dslink.util.handler.Handler;

public class AdvantechLink {
	
	Node node;
	final Map<AdvantechProject, ScheduledFuture<?>> futures = new ConcurrentHashMap<AdvantechProject, ScheduledFuture<?>>();

	private AdvantechLink(Node node) {
		this.node = node;
	}

	public static void start(Node parent) {
		final AdvantechLink adv = new AdvantechLink(parent);
		adv.init();
	}

	private void init() {
		restoreLastSession();	
		
		Action act = new Action(Permission.READ, new LogonHandler());
		act.addParameter(new Parameter("Name", ValueType.STRING));
		act.addParameter(new Parameter("IP", ValueType.STRING));
		act.addParameter(new Parameter("Username", ValueType.STRING));
		act.addParameter(new Parameter("Password", ValueType.STRING).setEditorType(EditorType.PASSWORD));
		act.addParameter(new Parameter("Polling interval", ValueType.NUMBER, new Value(5)));
		node.createChild("add connection").setAction(act).build().setSerializable(false);
		
		
	}
	
	private void restoreLastSession() {
		if (node.getChildren() == null) return;
		for (Node child: node.getChildren().values()) {
			Value ip = child.getAttribute("IP");
			Value user = child.getAttribute("Username");
			Value pass = child.getAttribute("Password");
			Value interv = child.getAttribute("Polling interval");
			if (ip!=null && user!=null && pass!=null && interv!=null) {
				//child.clearChildren();
				AdvantechConn ac = new AdvantechConn(getMe(), child);
				ac.restoreLastSession();
				//ac.init();
			} else if (!child.getName().equals("defs") && child.getAction() == null) {
				node.removeChild(child);
			}
		}
	}

	private class LogonHandler implements Handler<ActionResult> {
		public void handle(ActionResult event) {
			String name = event.getParameter("Name", ValueType.STRING).getString();
			String ip = event.getParameter("IP", ValueType.STRING).getString();
			String user = event.getParameter("Username", ValueType.STRING).getString();
			String pass = event.getParameter("Password", new Value("")).getString();
			double interv = event.getParameter("Polling interval", ValueType.NUMBER).getNumber().doubleValue();
			
			Node child = node.createChild(name).build();
			child.setAttribute("IP", new Value(ip));
			child.setAttribute("Username", new Value(user));
			child.setAttribute("Password", new Value(pass));
			child.setAttribute("Polling interval", new Value(interv));
			
			AdvantechConn ac = new AdvantechConn(getMe(), child);
			ac.init();
		}
	}
	
	void setupTag(final AdvantechTag at) {
		at.node.getListener().setOnSubscribeHandler(new Handler<Node>() {
			public void handle(final Node event) {
				at.project.subscribe(at);
			}
		});
		at.node.getListener().setOnUnsubscribeHandler(new Handler<Node>() {
			public void handle(final Node event) {
				at.project.unsubscribe(at);
			}
		});
	}

	private AdvantechLink getMe() {
		return this;
	}

}
