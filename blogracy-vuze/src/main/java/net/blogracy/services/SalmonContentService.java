package net.blogracy.services;

import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;

import net.blogracy.logging.Logger;
import net.blogracy.messaging.BlogracyContentMessageListener;
import net.blogracy.messaging.MessagingManager;
import net.blogracy.messaging.impl.BlogracyContent;
import net.blogracy.messaging.impl.BlogracyContentAccepted;
import net.blogracy.messaging.impl.BlogracyContentListRequest;
import net.blogracy.messaging.impl.BlogracyContentListResponse;
import net.blogracy.messaging.impl.BlogracyContentRejected;

import org.gudy.azureus2.plugins.PluginInterface;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class SalmonContentService implements MessageListener,
		BlogracyContentMessageListener {

	private Session session;
	private Destination queue;
	private MessageProducer producer;
	private MessageConsumer consumer;

	protected MessagingManager messagingManager;

	public SalmonContentService(Connection connection, PluginInterface plugin) {
		messagingManager = new MessagingManager(plugin);
		messagingManager.addListener(this);
		try {

			session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

			queue = session.createQueue("salmonContentService");
			producer = session.createProducer(queue);
			producer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
			consumer = session.createConsumer(queue);
			consumer.setMessageListener(this);

		} catch (JMSException e) {
			Logger.error("JMS error: creating Salmon Content service");
		}
	}

	@Override
	public void onMessage(final Message request) {
		TextMessage textRequest = (TextMessage) request;
		String text;
		try {
			text = textRequest.getText();

			Logger.info("Salmon Content service:" + text + ";");

			JSONObject record = new JSONObject(text);

			if (record.has("request"))
				handleRequest(record);
			else if (record.has("response"))
				handleResponse(record);
		} catch (JMSException e1) {
			e1.printStackTrace();
		} catch (JSONException e) {
			e.printStackTrace();
		}

	}
	
	
	protected void handleResponse(JSONObject record) throws JSONException
	{
		if (!record.has("response"))
			return;
		
		String responseType = record.getString("response");
		
		if(responseType.equalsIgnoreCase("contentListQueryResponse"))
		{
			JSONArray contentData = record.getJSONArray("contentData");
			String userQueried = record.getString("queryUserId");
			String userReplying = record.getString("currentUserId");
			
			sendBlogracyContentListResponse(userReplying, userQueried, contentData);
		}
	}
	
	protected void handleRequest(JSONObject record) throws JSONException
	{
		if (!record.has("request") || !record.has("currentUserId"))
			return;

		String requestType = record.getString("request");
		String userRequesting = record.getString("currentUserId");

		if (requestType.equalsIgnoreCase("contentList")) {
			sendBlogracyContentListRequest(userRequesting);
		} else if (requestType.equalsIgnoreCase("contentAccepted")) {
			if (!record.has("contentId"))
				return;

			String contentId = record.getString("contentId");
			sendBlogracyContentAccepted(userRequesting, contentId);
		} else if (requestType.equalsIgnoreCase("contentRejected")) {
			if (!record.has("contentId"))
				return;

			String contentId = record.getString("contentId");
			sendBlogracyContentRejected(userRequesting, contentId);
		} else if (requestType.equalsIgnoreCase("connectToFriends")) {
			// Connect to my friends list
			connectToSwarm(userRequesting);

			if (!record.has("friendsList"))
				return;

			JSONArray friendsListArray = record.getJSONArray("friendsList");
			for (int i = 0; i < friendsListArray.length(); ++i) {
				connectToSwarm(friendsListArray.get(i).toString());
			}

		} else if (requestType.equalsIgnoreCase("content")) {
			if (!record.has("destinationUserId"))
				return;

			String destinationUserId = record
					.getString("destinationUserId");
			String contentData = record.getString("contentData");
			sendBlogracyContent(userRequesting, destinationUserId,
					contentData);
		}
	}
	

	protected void sendBlogracyContentListRequest(String currentUserId) {
		messagingManager.sendContentListRequest(currentUserId);
	}

	protected void sendBlogracyContentAccepted(String currentUserId,
			String contentId) {
		messagingManager.sendContentAccepted(currentUserId, contentId);
	}

	protected void sendBlogracyContentRejected(String currentUserId,
			String contentId) {
		messagingManager.sendContentRejected(currentUserId, contentId);
	}

	protected void connectToSwarm(String userId) {
		if (messagingManager.getSwarm(userId) == null)
			messagingManager.addSwarm(userId);
	}

	protected void sendBlogracyContent(String currentUserId,
			String destinationUserId, String contentData) {
		messagingManager.sendContentMessage(destinationUserId,
				destinationUserId, contentData);
	}
	
	protected void sendBlogracyContentListResponse(String currentUserId, String queriedUserId, JSONArray contentsList)	{
		messagingManager.sendContentListResponse(currentUserId, queriedUserId, contentsList);
	}

	@Override
	public void blogracyContentReceived(BlogracyContent message) {
		if (message == null)
			return;

		TextMessage response;
		try {
			response = session.createTextMessage();
			
			JSONObject record = new JSONObject();
			record.put("request", "contentReceived");
			record.put("currentUserId", message.getSenderUserId());
			// TODO: myself default? Is this useful?
			record.put("destinationUserId", message.getSenderUserId() );
			record.put("contentData", message.getContent() );
			
			response.setText(record.toString());

			producer.send(queue, response);
		} catch (JMSException e) {
			e.printStackTrace();
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	@Override
	public void blogracyContentListRequestReceived(
			BlogracyContentListRequest message) {
		if (message == null)
			return;

		TextMessage response;
		try {
			response = session.createTextMessage();
			
			JSONObject record = new JSONObject();
			record.put("request", "contentListQuery");
			record.put("queryUserId", message.getSenderUserId());
			/*
			 * 
			 * response.setJMSCorrelationID(request .getJMSCorrelationID());
			 */
			
			response.setText(record.toString());

			producer.send(queue, response);
		} catch (JMSException e) {
			e.printStackTrace();
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	@Override
	public void blogracyContentListResponseReceived(
			BlogracyContentListResponse message) {
		if (message == null)
			return;

		TextMessage response;
		try {
			response = session.createTextMessage();
			
			JSONObject record = new JSONObject();
			record.put("request", "contentListReceived");
			record.put("senderUserId", message.getSenderUserId());
			record.put("contentData", message.getContent());
			/*
			 * 
			 * response.setJMSCorrelationID(request .getJMSCorrelationID());
			 */
			
			response.setText(record.toString());

			producer.send(queue, response);
		} catch (JMSException e) {
			e.printStackTrace();
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	@Override
	public void blogracyContentAcceptedReceived(BlogracyContentAccepted message) {
		if (message == null)
			return;

		TextMessage response;
		try {
			response = session.createTextMessage();
			
			JSONObject record = new JSONObject();
			record.put("request", "contentAcceptedInfo");
			record.put("contentUserId", message.getSenderUserId());
			JSONObject content = new JSONObject(message.getContent());
			record.put("contentId", content.getString("contentId"));
			/*
			 * 
			 * response.setJMSCorrelationID(request .getJMSCorrelationID());
			 */
			
			response.setText(record.toString());

			producer.send(queue, response);
		} catch (JMSException e) {
			e.printStackTrace();
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	@Override
	public void blogracyContentRejectedReceived(BlogracyContentRejected message) {
		if (message == null)
			return;

		TextMessage response;
		try {
			response = session.createTextMessage();
			
			JSONObject record = new JSONObject();
			record.put("request", "contentRejectedInfo");
			record.put("contentUserId", message.getSenderUserId());
			JSONObject content = new JSONObject(message.getContent());
			record.put("contentId", content.getString("contentId"));
			/*
			 * 
			 * response.setJMSCorrelationID(request .getJMSCorrelationID());
			 */
			
			response.setText(record.toString());

			producer.send(queue, response);
		} catch (JMSException e) {
			e.printStackTrace();
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}
}