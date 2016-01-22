package advantech;

import java.util.HashMap;
import java.util.Map;

import org.dsa.iot.dslink.node.Node;
import org.dsa.iot.dslink.node.value.Value;
import org.dsa.iot.dslink.node.value.ValueType;
import org.dsa.iot.dslink.util.json.JsonArray;
import org.dsa.iot.dslink.util.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import advantech.Utils.ApiException;

public class AdvantechBlock {
	private final static Logger LOGGER;
	static {
        LOGGER = LoggerFactory.getLogger(AdvantechBlock.class);
    }
	
	AdvantechNode scada;
	String name;
	Node node;
	
	AdvantechBlock(AdvantechNode scada, JsonObject json, Node parentNode) {
		this.scada = scada;
		this.name = (String) json.get("Name");
		
		this.node = parentNode.createChild(name).setValueType(ValueType.NUMBER).setValue(new Value((Number) json.get("ID"))).build();
		init();
		
//		node.getListener().setOnListHandler(new Handler<Node>() {
//			private boolean done = false;
//			public void handle(Node event) {
//				if (done) return;
//				done = true;
//				init();
//			}
//		});
	}
	
	void init() {
		
		Map<String, String> pars = new HashMap<String, String>();
		pars.put("HostIp", scada.project.conn.node.getAttribute("IP").getString());
		pars.put("ProjectName", scada.project.name);
		pars.put("NodeName", scada.name);
		pars.put("BlockName", name);
		
		try {
			String response = Utils.sendGet(Utils.NODE_BLOCK_DETAIL, pars, scada.project.conn.auth);
			if (response != null) {
//				LOGGER.info("BLOCKDETAIL: " + response);
				JsonArray tags = (JsonArray) new JsonObject(response).get("Tags");
				JsonArray tagRequestList = new JsonArray();
				for (Object o: tags) {
					JsonObject tagReq = new JsonObject();
					tagReq.put("Name", ((JsonObject) o).get("TagName"));
//					tagReq.put("Attributes", new JsonArray("[{\"Name\":\"ALL\"}]"));
					tagReq.put("Attributes", new JsonArray("[{\"Name\":\"NAME\"},{\"Name\":\"DESCRP\"},{\"Name\":\"PADDRS\"},{\"Name\":\"NODE\"},{\"Name\":\"COM\"},{\"Name\":\"DEVNM\"},{\"Name\":\"TYPE\"},{\"Name\":\"DESCR0\"}, {\"Name\":\"DESCR1\"},{\"Name\":\"DESCR2\"},{\"Name\":\"DESCR3\"},{\"Name\":\"DESCR4\"},{\"Name\":\"DESCR5\"},{\"Name\":\"DESCR6\"},{\"Name\":\"DESCR7\"}]"));
					tagRequestList.add(tagReq);
				}
				JsonObject req = new JsonObject();
				req.put("Tags", tagRequestList);
				response = Utils.sendPost(Utils.PROJ_TAG_DETAIL, pars, scada.project.conn.auth, req.toString());
				LOGGER.debug("REQUEST:" + req.toString() + " | " + "RESPONSE: " + response);
				JsonArray tagDetail = (JsonArray) new JsonObject(response).get("Tags");
				for (Object o: tagDetail) {
					AdvantechTag at = new AdvantechTag(this, (JsonObject) o);
					at.init();
				}
			}
		} catch (ApiException e) {
			// TODO Auto-generated catch block
			LOGGER.debug("", e);
		}
	}
}
