package advantech;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.dsa.iot.dslink.node.Node;
import org.dsa.iot.dslink.node.Permission;
import org.dsa.iot.dslink.node.Writable;
import org.dsa.iot.dslink.node.actions.Action;
import org.dsa.iot.dslink.node.actions.ActionResult;
import org.dsa.iot.dslink.node.actions.Parameter;
import org.dsa.iot.dslink.node.actions.ResultType;
import org.dsa.iot.dslink.node.actions.table.Row;
import org.dsa.iot.dslink.node.actions.table.Table;
import org.dsa.iot.dslink.node.actions.table.Table.Mode;
import org.dsa.iot.dslink.node.value.Value;
import org.dsa.iot.dslink.node.value.ValuePair;
import org.dsa.iot.dslink.node.value.ValueType;
import org.dsa.iot.dslink.util.handler.CompleteHandler;
import org.dsa.iot.dslink.util.handler.Handler;
import org.dsa.iot.dslink.util.json.JsonArray;
import org.dsa.iot.dslink.util.json.JsonObject;
import org.dsa.iot.historian.stats.GetHistory;
import org.dsa.iot.historian.stats.interval.IntervalParser;
import org.dsa.iot.historian.stats.rollup.Rollup;
import org.dsa.iot.historian.utils.QueryData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import advantech.Utils.ApiException;

public class AdvantechTag {
	private final static Logger LOGGER;
	static {
        LOGGER = LoggerFactory.getLogger(AdvantechTag.class);
    }

//	AdvantechLevel parent;
	AdvantechProject project;
	AdvantechBlock block;
	Node node;
	Node displayNode;
	String name;
	boolean awaitingSet = false;
	
	final String[] states = new String[8];
	
	AdvantechTag(AdvantechProject project, JsonObject json) {
//		this.parent = parent;
		this.project = project;
		this.block = null;
		setup(json);
	}
	
	AdvantechTag(AdvantechProject project, Node node) {
		this.project = project;
		this.block = null;
		this.node = node;
		this.name = node.getName();
		this.displayNode = node.getChild("Display value");
		setStates();
		setListeners();
		
	}
	
	AdvantechTag(AdvantechBlock block, JsonObject json) {
		this.block = block;
		this.project = block.scada.project;
		setup(json);
	}
	
	AdvantechTag(AdvantechBlock block, Node node) {
		this.block = block;
		this.project = block.scada.project;
		this.node = node;
		this.name = node.getName();
		this.displayNode = node.getChild("Display value");
		setStates();
		setListeners();
		
	}
	
	private void setup(JsonObject json) {
		if (json.get("NAME") != null) this.name = (String) json.get("NAME");
		else this.name = (String) json.get("Name");
		ValueType vt;
		ValueType dispvt = null;
		String type = json.get("TYPE");
		if (isAnalog(type)) vt = ValueType.NUMBER;
		else if (isDiscrete(type)) {
			vt = ValueType.NUMBER;
			Set<String> enums = new HashSet<String>();
			for (int i=0; i<states.length; i++) {
				String s = json.get("DESCR"+i);
				states[i] = s;
				if (s != null) enums.add(s);
			}
			dispvt = ValueType.makeEnum(enums);
		} else vt = ValueType.STRING;
		Node parent = sort(json);
		this.node = parent.createChild(name).setValueType(vt).build();
		node.setAttribute("_dstype", new Value("tag"));
		node.setAttribute("_json", new Value(json.toString()));
		for (Entry<String, Object> entry: json) {
			if (!entry.getKey().equals("NAME") && !entry.getKey().equals("Name")) {
				node.setAttribute(entry.getKey(), new Value((String) entry.getValue()));
			}
		}
		node.getListener().setValueHandler(new SetHandler());
		node.setWritable(Writable.WRITE);
		if (isDiscrete(type)) {
			this.displayNode = node.createChild("Display value").setValueType(dispvt).build();
			displayNode.getListener().setValueHandler(new DisplaySetHandler());
			displayNode.setWritable(Writable.WRITE);
		}
	}
	
	private void setStates() {
		JsonObject json = new JsonObject(node.getAttribute("_json").getString());
		String type = json.get("TYPE");
		if (isDiscrete(type)) {
			for (int i=0; i<states.length; i++) {
				String s = json.get("DESCR"+i);
				states[i] = s;
			}
		}
	}
	
	private void setListeners() {
		node.getListener().setValueHandler(new SetHandler());
		node.setWritable(Writable.WRITE);
		if (displayNode != null) {
			displayNode.getListener().setValueHandler(new DisplaySetHandler());
			displayNode.setWritable(Writable.WRITE);
		}
	}
	
	
	Node sort(JsonObject json) {
		if (block != null) return block.node;
		else {
			String scadaName = json.get("NODE");
			String portNum = json.get("COM");
			String devName = json.get("DEVNM");
			if (scadaName == null) return project.node;
			AdvantechNode scada = project.scadaList.get(scadaName);
			if (scada == null) return project.node;
			if (portNum == null) return scada.node;
			if (!scada.loaded) scada.node.getListener().postListUpdate();
			AdvantechPort port = scada.portList.get(portNum);
			if (port == null) return scada.node;
			if (devName == null) return port.node;
			if (!port.loaded) port.node.getListener().postListUpdate();
			AdvantechDevice dev = port.deviceList.get(devName);
			if (dev == null) return port.node;
			return dev.node;
			
		}
	}
	
	void init() {
		project.conn.link.setupTag(this);
		if (node.getLink().getSubscriptionManager().hasValueSub(node)) project.subscribe(this);
//		tryDataLog();
		GetHistory.initAction(node, new Gh());
		
		Action act = new Action(Permission.READ, new Handler<ActionResult>() {
			public void handle(ActionResult event) {
				handleGetDataLog(event);
			}
		});
		act.addParameter(new Parameter("Start Time", ValueType.STRING).setPlaceHolder("2016-09-15T14:45:00"));
		act.addParameter(new Parameter("Interval Type", ValueType.makeEnum("Days", "Hours", "Minutes", "Seconds")));
		act.addParameter(new Parameter("Interval", ValueType.NUMBER));
		act.addParameter(new Parameter("Records", ValueType.NUMBER));
		act.addParameter(new Parameter("Data Type", ValueType.makeEnum("Last", "Min", "Max", "Avg")));
		act.addResult(new Parameter("Value", ValueType.STRING));
		act.setResultType(ResultType.TABLE);
		Node anode = node.getChild("get data log");
		if (anode == null) node.createChild("get data log").setAction(act).build().setSerializable(false);
		else anode.setAction(act);
	}
	
	static boolean isAnalog(String type) {
		return "ANALOG".equals(type) || "TEMP".equals(type) || "TSP".equals(type) || "A".equals(type);
	}
	
	static boolean isDiscrete(String type) {
		return "DIGITAL".equals(type) || "ST".equals(type) || "TA".equals(type) || "D".equals(type);
	}
	
	private class DisplaySetHandler implements Handler<ValuePair> {
		public void handle(ValuePair event) {
			if (!event.isFromExternalSource()) return;
			
			Map<String, String> pars = new HashMap<String, String>();
			pars.put("ProjectName", project.name);
			pars.put("HostIp", project.conn.node.getAttribute("IP").getString());
			
			JsonObject json = new JsonObject();
			JsonArray jarr = new JsonArray();
			JsonObject valobj = new JsonObject();
			valobj.put("Name", name);
			
			int val = indexOfState(event.getCurrent().getString());
			if (val < 0) {
				LOGGER.debug("Invalid state for discrete value");
				return;
			}
			valobj.put("Value", val);
			jarr.add(valobj);
			json.put("Tags", jarr);
			try {
				Utils.sendPost(Utils.SET_TAG_VALUE, pars, project.conn.auth, json.toString());
			} catch (ApiException e) {
				// TODO Auto-generated catch block
				LOGGER.debug("", e);
			}
		}
	}
	
	private class SetHandler implements Handler<ValuePair> {
		public void handle(ValuePair event) {
			if (!event.isFromExternalSource()) return;
			
			synchronized(AdvantechTag.this) {
				awaitingSet = true;
			}
			
			Map<String, String> pars = new HashMap<String, String>();
			pars.put("ProjectName", project.name);
			pars.put("HostIp", project.conn.node.getAttribute("IP").getString());
			
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
					Utils.sendPost(Utils.SET_TAG_VALUE, pars, project.conn.auth, json.toString());
				} catch (ApiException e) {
					// TODO Auto-generated catch block
					LOGGER.debug("", e);
				}
			} else if (isDiscrete(type)) {
				int val = event.getCurrent().getNumber().intValue();
				if (val < 0) {
					LOGGER.debug("Invalid state for discrete value");
					return;
				}
				valobj.put("Value", val);
				jarr.add(valobj);
				json.put("Tags", jarr);
				try {
					Utils.sendPost(Utils.SET_TAG_VALUE, pars, project.conn.auth, json.toString());
				} catch (ApiException e) {
					// TODO Auto-generated catch block
					LOGGER.debug("", e);
				}
			} else {
				valobj.put("Value", event.getCurrent().getString());
				jarr.add(valobj);
				json.put("Tags", jarr);
				try {
					Utils.sendPost(Utils.SET_TAG_VAUE_TEXT, pars, project.conn.auth, json.toString());
				} catch (ApiException e) {
					// TODO Auto-generated catch block
					LOGGER.debug("", e);
				}
			}
			
			synchronized(AdvantechTag.this) {
				awaitingSet = false;
			}
		}
	}
	
	private int indexOfState(String state) {
		for (int i=0; i<states.length; i++) {
			if (state.equals(states[i])) return i;
		}
		return -1;
	}
	
//	private void tryDataLog() {
//		JsonObject json = new JsonObject();
//		json.put("StartTime", "2015-11-07 06:50:00");
//		double interval = 1;
//		String type = "M";
//		json.put("IntervalType", type);
//		json.put("Interval", interval);
//		json.put("Records", 50);
//		JsonObject tagObj = new JsonObject();
//		tagObj.put("Name", name);
//		tagObj.put("DataType", "0");
//		JsonArray jarr = new JsonArray();
//		jarr.add(tagObj);
//		json.put("Tags", jarr);
//		
//		Map<String, String> pars = new HashMap<String, String>();
//		pars.put("ProjectName", device.port.scada.project.name);
//		pars.put("HostIp", device.port.scada.project.conn.node.getAttribute("IP").getString());
//		
//		try {
//			String response = Utils.sendPost(Utils.DATA_LOG, pars, device.port.scada.project.conn.auth, json.toString());
//			LOGGER.info(response);
//			response = Utils.sendGet(Utils.SERVER_TIME, pars, device.port.scada.project.conn.auth);
//			LOGGER.info(response);
//		} catch (ApiException e) {
//			// TODO Auto-generated catch block
//			LOGGER.debug("", e);
//		}
//	}
	
	private void handleGetDataLog(ActionResult event) {
		JsonObject json = new JsonObject();
		DateFormat advdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		advdf.setTimeZone(project.conn.timezone);
		DateFormat dsadf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
		
		String startStr = event.getParameter("Start Time", ValueType.STRING).getString();
		String intervType = event.getParameter("Interval Type").getString().substring(0, 1);
		int interval = event.getParameter("Interval", ValueType.NUMBER).getNumber().intValue();
		int records = event.getParameter("Records", ValueType.NUMBER).getNumber().intValue();
		String dataTypeStr = event.getParameter("Data Type").getString();		
		
		Date start;
		try {
			start = dsadf.parse(startStr);
		} catch (ParseException e) {
			try {
				start = advdf.parse(startStr);
			} catch (ParseException e1) {
				return;
			}
		}
		
		int dataType = 0;
		if ("Min".equals(dataTypeStr)) {
			dataType = 1;
		} else if ("Max".equals(dataTypeStr)) {
			dataType = 2;
		} else if ("Avg".equals(dataTypeStr)) {
			dataType = 3;
		}
		
		json.put("StartTime", advdf.format(start));
		json.put("IntervalType", intervType);
		json.put("Interval", interval);
		json.put("Records", records);
		JsonObject tagObj = new JsonObject();
		tagObj.put("Name", name);
		tagObj.put("DataType", dataType);
		JsonArray jarr = new JsonArray();
		jarr.add(tagObj);
		json.put("Tags", jarr);
		
		Map<String, String> pars = new HashMap<String, String>();
		pars.put("ProjectName", project.name);
		pars.put("HostIp", project.conn.node.getAttribute("IP").getString());
		
		try {
			LOGGER.debug("sending Data Log request: " + json.toString());
			String response = Utils.sendPost(Utils.DATA_LOG, pars, project.conn.auth, json.toString());
			if (response != null) {
				LOGGER.debug("recieved Data Log response: " + response);
				JsonArray logs = (JsonArray) new JsonObject(response).get("DataLog");
				JsonArray vals = ((JsonObject) logs.get(0)).get("Values");
				Table table = event.getTable();
				table.setMode(Mode.APPEND);
				for (Object o: vals) {
					Row row = Row.make(new Value((String) o));
					table.addRow(row);
				}
			}
		} catch (ApiException e) {
			// TODO Auto-generated catch block
			LOGGER.debug("", e);
		}
		
 	}
	
	private class Gh extends GetHistory {

	public Gh() {
		super(node, null);
	}
	
	@Override
	protected void query(long from,
            long to,
            Rollup.Type type,
            IntervalParser parser,
            CompleteHandler<QueryData> handler) {
		
				
		JsonObject json = new JsonObject();
		DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		df.setTimeZone(project.conn.timezone);
		
		String intervType = "S";
		long start, interval, intervalMillis;
		int recs, rolltype;
		
		switch (type) {
		case MIN: rolltype = 1;break;
		case MAX: rolltype = 2;break;
		case AVERAGE: rolltype = 3;break;
		default: rolltype = 0;
		}
		
		if (parser != null) {
			intervalMillis = parser.incrementTime();
			
			recs = (int) ((to - from)/intervalMillis);
			if (recs < 1) recs = 1;
		} else {
			recs = 10;
			intervalMillis = (to - from)/recs;
		}
		
		if (rolltype == 0) {
			recs += 1;
			start = from - intervalMillis;
		} else {
			start = from;
		}
		
		interval = intervalMillis/1000;
		
		json.put("StartTime", df.format(new Date(start)));
		json.put("IntervalType", intervType);
		json.put("Interval", interval);
		json.put("Records", recs);
		JsonObject tagObj = new JsonObject();
		tagObj.put("Name", name);
		tagObj.put("DataType", rolltype);
		JsonArray jarr = new JsonArray();
		jarr.add(tagObj);
		json.put("Tags", jarr);
		
		Map<String, String> pars = new HashMap<String, String>();
		pars.put("ProjectName", project.name);
		pars.put("HostIp", project.conn.node.getAttribute("IP").getString());
		
		try {
			LOGGER.debug("sending Data Log request: " + json.toString());
			String response = Utils.sendPost(Utils.DATA_LOG, pars, project.conn.auth, json.toString());
			if (response != null) {
				LOGGER.debug("recieved Data Log response: " + response);
				JsonArray logs = (JsonArray) new JsonObject(response).get("DataLog");
				JsonArray vals = ((JsonObject) logs.get(0)).get("Values");
				for (int i=0; i<vals.size(); i++) {
					Object o = vals.get(i);
					long ts = start + (intervalMillis*(i+1));
					QueryData qd = new QueryData(new Value((String) o), ts);
					handler.handle(qd);
				}
			}
		} catch (ApiException e) {
			// TODO Auto-generated catch block
			LOGGER.debug("", e);
		} finally {
			handler.complete();
		}
		
	}
		
	}
	
}
