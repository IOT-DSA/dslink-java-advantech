package advantech;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
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
import org.dsa.iot.dslink.util.handler.CompleteHandler;
import org.dsa.iot.dslink.util.handler.Handler;
import org.dsa.iot.dslink.util.json.Json;
import org.dsa.iot.dslink.util.json.JsonArray;
import org.dsa.iot.dslink.util.json.JsonObject;
import org.dsa.iot.historian.database.Database;
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
	String name;
	
	final String[] states = new String[8];
	
	AdvantechTag(AdvantechProject project, JsonObject json) {
//		this.parent = parent;
		this.project = project;
		this.block = null;
		setup(json);
	}
	
	AdvantechTag(AdvantechBlock block, JsonObject json) {
		this.block = block;
		this.project = block.project;
		setup(json);
	}
	
	private void setup(JsonObject json) {
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
		if (block != null) this.node = block.node.createChild(name).setValueType(vt).build();
		else this.node = project.node.createChild(name).setValueType(vt).build();
		for (Entry<String, Object> entry: json) {
			if (!entry.getKey().equals("NAME") && !entry.getKey().equals("Name")) {
				node.setAttribute(entry.getKey(), new Value((String) entry.getValue()));
			}
		}
		node.getListener().setValueHandler(new SetHandler());
		node.setWritable(Writable.WRITE);
	}
	
	void init() {
		project.conn.link.setupTag(this);
		if (node.getLink().getSubscriptionManager().hasValueSub(node)) project.subscribe(this);
//		tryDataLog();
	}
	
	static boolean isAnalog(String type) {
		return "ANALOG".equals(type) || "TEMP".equals(type) || "TSP".equals(type) || "A".equals(type);
	}
	
	static boolean isDiscrete(String type) {
		return "DIGITAL".equals(type) || "ST".equals(type) || "TA".equals(type) || "D".equals(type);
	}
	
	private class SetHandler implements Handler<ValuePair> {
		public void handle(ValuePair event) {
			if (!event.isFromExternalSource()) return;
			
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
	
	private class Db extends Database {

		public Db() {
			super(node.getName(), null);
		}

		@Override
		public void write(String path, Value value, long ts) {
			throw new UnsupportedOperationException();
			
		}

		@Override
		public void query(String path, long from, long to, CompleteHandler<QueryData> handler) {
			JsonObject json = new JsonObject();
			DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
			json.put("StartTime", df.format(new Date(from)));
			double interval;
			String type;
			long intervalSeconds = (to - from)/1000L;
			if (intervalSeconds < 1000000L) {
				interval = (double) intervalSeconds;
				type = "S";
			}
			else {
				double intervalMinutes = intervalSeconds/60.0;
				if (intervalMinutes < 1000000) {
					interval = intervalMinutes;
					type = "M";
				} else {
					interval = intervalMinutes/60.0;
					type = "H";
				}
			}
			json.put("IntervalType", type);
			json.put("Interval", interval);
			json.put("Records", true);
			JsonObject tagObj = new JsonObject();
			tagObj.put("Name", name);
			tagObj.put("DataType", "0");
			JsonArray jarr = new JsonArray();
			jarr.add(tagObj);
			json.put("Tags", jarr);
			
			Map<String, String> pars = new HashMap<String, String>();
			pars.put("ProjectName", project.name);
			pars.put("HostIp", project.conn.node.getAttribute("IP").getString());
			
			try {
				String response = Utils.sendPost(Utils.DATA_LOG, pars, project.conn.auth, json.toString());
				if (response != null) {
					JsonArray logs = (JsonArray) Json.decodeMap(response).get("DataLog");
					JsonArray vals = ((JsonObject) logs.get(0)).get("Values");
				}
			} catch (ApiException e) {
				// TODO Auto-generated catch block
				LOGGER.debug("", e);
			}
		}

		@Override
		public QueryData queryFirst(String path) {
			throw new UnsupportedOperationException();
		}

		@Override
		public QueryData queryLast(String path) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void close() throws Exception {
			throw new UnsupportedOperationException();
			
		}

		@Override
		protected void performConnect() throws Exception {
			throw new UnsupportedOperationException();
			
		}

		@Override
		public void initExtensions(Node node) {
			throw new UnsupportedOperationException();
			
		}
		
	}
	
}
