package net.blogracy;

import net.blogracy.model.users.User;
import net.blogracy.controller.ActivitiesController;
import net.blogracy.controller.ChatController;
import net.blogracy.controller.CommentsController;
import net.blogracy.controller.DistributedHashTable;
import net.blogracy.controller.FileSharing;
import net.blogracy.config.Configurations;
import java.util.List;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.webapp.WebAppContext;

public class WebServer
{
    public static void main(String[] args) throws Exception
    {
        Server server = new Server(8080);
 
        WebAppContext context = new WebAppContext();
        context.setDescriptor("target/webapp/WEB-INF/web.xml");
        context.setResourceBase("target/webapp");
        context.setContextPath("/");
        context.setParentLoaderPriority(true);
 
        server.setHandler(context);
 
        server.start();
        // server.join();

        List<User> friends = Configurations.getUserConfig().getFriends();
       /* for (User friend : friends) {
            String hash = friend.getHash().toString();
            ChatController.createChannel(hash);
        }
        */
        
        CommentsController.getInstance().initializeConnection();
        
        int TOTAL_WAIT = 5 * 60 * 1000; // 1 minutes
        
        while (true) {
        	 ActivitiesController activities = ActivitiesController.getSingleton();
            String id = Configurations.getUserConfig().getUser().getHash().toString();
            activities.addFeedEntry(id, "" + new java.util.Date(), null);
        
            CommentsController.getInstance().getContentList(id);
            
            // List<User> friends = Configurations.getUserConfig().getFriends();
            int wait = TOTAL_WAIT / friends.size();
            for (User friend : friends) {
                DistributedHashTable.getSingleton().lookup(friend.getHash().toString());
                activities.getFeed(friend.getHash().toString());
                Thread.currentThread().sleep(wait);
            }
        }
    }
}
