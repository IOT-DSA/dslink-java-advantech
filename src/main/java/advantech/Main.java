package advantech;

import org.dsa.iot.dslink.DSLink;
import org.dsa.iot.dslink.DSLinkFactory;
import org.dsa.iot.dslink.DSLinkHandler;
import org.dsa.iot.dslink.node.Node;
import org.dsa.iot.dslink.node.NodeBuilder;
import org.dsa.iot.dslink.node.NodeManager;
import org.dsa.iot.dslink.node.Permission;
import org.dsa.iot.dslink.node.actions.Action;
import org.dsa.iot.historian.stats.GetHistory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main extends DSLinkHandler {
	private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);
	
	private DSLink link;
	
	public static void main(String[] args) {
		DSLinkFactory.start(args, new Main());
	}
	
	@Override
	public boolean isResponder() {
		return true;
	}
	
	@Override
	public void onResponderInitialized(DSLink link) {
		LOGGER.info("Initialized");
		
		this.link = link;
				
		NodeManager manager = link.getNodeManager();
        Node superRoot = manager.getNode("/").getNode();
        
        {
            
            NodeBuilder b = superRoot.createChild("defs");
            b.setSerializable(false);
            b.setHidden(true);
            Node node = b.build();

            b = node.createChild("profile");
            node = b.build();

            b = node.createChild("getHistory_");
            Action act = new Action(Permission.READ, null);
            GetHistory.initProfile(act);
            b.setAction(act);
            b.build();
            
        }
        
        AdvantechLink.start(superRoot);
	}
	
	@Override
	public Node onSubscriptionFail(String path) {
		NodeManager manager = link.getNodeManager();
		String[] split = NodeManager.splitPath(path);
        Node superRoot = manager.getSuperRoot();
        Node n = superRoot;
        int i = 0;
        while (i < split.length) {        	
        	n = n.getChild(split[i]);
        	n.getListener().postListUpdate();
        	i++;
        }
        return n;
	}
	
	@Override
	public Node onInvocationFail(final String path) {
		NodeManager manager = link.getNodeManager();
        String[] split = NodeManager.splitPath(path);
        Node superRoot = manager.getSuperRoot();
        Node n = superRoot;
        int i = 0;
        while (i < split.length) {        	
        	n = n.getChild(split[i]);
        	n.getListener().postListUpdate();
        	i++;
        }
        return n;
	}

}
