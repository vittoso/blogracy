package net.blogracy.controller;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.DeliveryMode;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;

import net.blogracy.config.Configurations;
import net.blogracy.controller.addendum.AddendumController;
import net.blogracy.errors.BlogracyItemNotFound;
import net.blogracy.model.hashes.Hashes;
import net.blogracy.model.users.User;
import net.blogracy.model.users.UserAddendumData;
import net.blogracy.model.users.UserData;
import net.blogracy.model.users.UserDataImpl;
import net.blogracy.model.users.Users;

import org.apache.activemq.ActiveMQConnection;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.shindig.protocol.conversion.BeanConverter;
import org.apache.shindig.protocol.conversion.BeanJsonConverter;
import org.apache.shindig.social.opensocial.model.ActivityEntry;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.google.inject.Binder;
import com.google.inject.Guice;
import com.google.inject.Module;
import com.google.inject.name.Names;

public class CommentsControllerImpl implements MessageListener, CommentsController {

	private ConnectionFactory connectionFactory;
	private Connection connection;
	private Session session;
	private Destination salmonContentQueue;
	private Destination salmonContentResponseQueue;
	private MessageProducer producer;
	private MessageConsumer consumer;
	private Boolean isInitialized = false;
	private Object lockObject = new Object();

	static final DateFormat ISO_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");

	private static BeanJsonConverter CONVERTER = new BeanJsonConverter(Guice.createInjector(new Module() {
		@Override
		public void configure(Binder b) {
			b.bind(BeanConverter.class).annotatedWith(Names.named("shindig.bean.converter.json")).to(BeanJsonConverter.class);
		}
	}));

	private static final CommentsController THE_INSTANCE = new CommentsControllerImpl();
	private static final FileSharing sharing = FileSharingImpl.getSingleton();
	AddendumController addendumController = AddendumController.getSingleton();

	public static CommentsController getInstance() {
		return THE_INSTANCE;
	}

	protected CommentsControllerImpl() {
		ISO_DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("UTC"));
		connectionFactory = new ActiveMQConnectionFactory(ActiveMQConnection.DEFAULT_BROKER_URL);
		try {
			connection = connectionFactory.createConnection();
			connection.start();
			session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

			salmonContentQueue = session.createQueue("salmonContentService");
			salmonContentResponseQueue = session.createQueue("salmonContentResponseService");

			producer = session.createProducer(salmonContentQueue);
			producer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);

			consumer = session.createConsumer(salmonContentResponseQueue);
			consumer.setMessageListener(this);
		} catch (JMSException e) {
			e.printStackTrace();
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see net.blogracy.controller.CommentsController#initializeConnection()
	 */
	@Override
	public void initializeConnection() {
		if (!isInitialized) {
			User localUser = Configurations.getUserConfig().getUser();
			synchronized (lockObject) {
				if (!isInitialized) {
					connectToFriends(localUser.getHash().toString());
					isInitialized = true;
				}
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * net.blogracy.controller.CommentsController#getComments(java.lang.String,
	 * java.lang.String)
	 */
	@Override
	public List<ActivityEntry> getComments(final String userId, final String objectId) {
		List<ActivityEntry> comments = new ArrayList<ActivityEntry>();
		UserData data = sharing.getUserData(userId);
		comments.addAll(data.getCommentsByObjectId(objectId));
		UserAddendumData addendumData = sharing.getUserAddendumData(userId);
		comments.addAll(addendumData.getCommentsByObjectId(objectId));
		return comments;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * net.blogracy.controller.CommentsController#addComment(java.lang.String,
	 * java.lang.String, java.lang.String, java.lang.String)
	 */
	@Override
	public void addComment(final String commentedUserId, final String commentingUserId, final String text, final String objectId) throws BlogracyItemNotFound {
		this.addComment(commentedUserId, commentingUserId, text, objectId, ISO_DATE_FORMAT.format(new Date()));
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * net.blogracy.controller.CommentsController#addComment(java.lang.String,
	 * java.lang.String, java.lang.String, java.lang.String, java.lang.String)
	 */
	@Override
	public void addComment(final String commentedUserId, final String commentingUserId, final String text, final String commentedObjectId, final String publishedDate) throws BlogracyItemNotFound {

		User commentingUser = null;
		if (commentedUserId.equals(commentingUserId)) {
			UserData data = sharing.getUserData(commentedUserId);
			commentingUser = data.getUser();
			data.addComment(commentingUser, text, commentedObjectId, publishedDate);
			try {
				String dbUri = sharing.seedUserData(data);
				DistributedHashTable.getSingleton().store(commentedUserId, commentedUserId, dbUri, publishedDate);
			} catch (JSONException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		} else {
			commentingUser = Configurations.getUserConfig().getUser();

			// This shouldn't happen... anyway a new user with the requested
			// userHash is built (maybe it should throw an exception)
			if (commentingUser == null)
				commentingUser = Users.newUser(Hashes.fromString(commentingUserId));

			// Getting commented user's data in order to build a comment
			// ActivityObject
			UserData data = sharing.getUserData(commentedUserId);

			try {
				JSONObject requestObj = new JSONObject();
				requestObj.put("request", "content");
				requestObj.put("currentUserId", commentingUserId);
				requestObj.put("destinationUserId", commentedUserId);
				requestObj.put("contentData", data.createComment(commentingUser, text, commentedObjectId, publishedDate));

				TextMessage request = session.createTextMessage();
				request.setText(requestObj.toString());
				producer.send(salmonContentQueue, request);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * net.blogracy.controller.CommentsController#connectToFriends(java.lang
	 * .String)
	 */
	@Override
	public void connectToFriends(String userId) {
		List<User> friendList = Configurations.getUserConfig().getFriends();
		try {
			JSONObject requestObj = new JSONObject();
			requestObj.put("request", "connectToFriends");
			requestObj.put("currentUserId", userId);
			JSONArray friends = new JSONArray();
			for (User friend : friendList) {
				friends.put(friend.getHash().toString());
			}
			requestObj.put("friendsList", friends);
			TextMessage request = session.createTextMessage();
			request.setText(requestObj.toString());
			producer.send(salmonContentQueue, request);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * net.blogracy.controller.CommentsController#getContentList(java.lang.String
	 * )
	 */
	@Override
	public void getContentList(String userId) {
		try {
			JSONObject requestObj = new JSONObject();
			requestObj.put("request", "contentList");
			requestObj.put("currentUserId", userId);
			TextMessage request = session.createTextMessage();
			request.setText(requestObj.toString());
			producer.send(salmonContentQueue, request);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * net.blogracy.controller.CommentsController#acceptContent(java.lang.String
	 * , java.lang.String)
	 */
	@Override
	public void acceptContent(String userId, String contentId) {
		try {
			JSONObject requestObj = new JSONObject();
			requestObj.put("request", "contentAccepted");
			requestObj.put("currentUserId", userId);
			requestObj.put("contentId", contentId);
			TextMessage request = session.createTextMessage();
			request.setText(requestObj.toString());
			producer.send(salmonContentQueue, request);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * net.blogracy.controller.CommentsController#rejectContent(java.lang.String
	 * , java.lang.String)
	 */
	@Override
	public void rejectContent(String userId, String contentId) {
		try {
			JSONObject requestObj = new JSONObject();
			requestObj.put("request", "contentRejected");
			requestObj.put("currentUserId", userId);
			requestObj.put("contentId", contentId);
			TextMessage request = session.createTextMessage();
			request.setText(requestObj.toString());
			producer.send(salmonContentQueue, request);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * net.blogracy.controller.CommentsController#sendContentListResponse(java
	 * .lang.String, java.lang.String, org.json.JSONArray)
	 */
	@Override
	public void sendContentListResponse(String userId, String queryUserId, JSONArray contentData) {
		try {
			JSONObject requestObj = new JSONObject();
			requestObj.put("response", "contentListQueryResponse");
			requestObj.put("queryUserId", queryUserId);
			requestObj.put("contentData", contentData);
			requestObj.put("currentUserId", userId);
			TextMessage request = session.createTextMessage();
			request.setText(requestObj.toString());
			producer.send(salmonContentQueue, request);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * net.blogracy.controller.CommentsController#sendBullyElectionMessage(java
	 * .lang.String, java.lang.String)
	 */
	@Override
	public void sendBullyElectionMessage(String channelUserId, String senderUserId) {
		try {
			JSONObject requestObj = new JSONObject();
			requestObj.put("bullyMessageType", "election");
			requestObj.put("action", "send");
			requestObj.put("channelUserId", channelUserId);
			requestObj.put("senderUserId", senderUserId);
			TextMessage request = session.createTextMessage();
			request.setText(requestObj.toString());
			producer.send(salmonContentQueue, request);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * net.blogracy.controller.CommentsController#sendBullyAnswerMessage(java
	 * .lang.String, java.lang.String)
	 */
	@Override
	public void sendBullyAnswerMessage(String channelUserId, String senderUserId) {
		try {
			JSONObject requestObj = new JSONObject();
			requestObj.put("bullyMessageType", "answer");
			requestObj.put("action", "send");
			requestObj.put("channelUserId", channelUserId);
			requestObj.put("senderUserId", senderUserId);
			TextMessage request = session.createTextMessage();
			request.setText(requestObj.toString());
			producer.send(salmonContentQueue, request);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * net.blogracy.controller.CommentsController#sendBullyCoordinatorMessage
	 * (java.lang.String, java.lang.String)
	 */
	@Override
	public void sendBullyCoordinatorMessage(String channelUserId, String senderUserId) {
		try {
			JSONObject requestObj = new JSONObject();
			requestObj.put("bullyMessageType", "coordinator");
			requestObj.put("action", "send");
			requestObj.put("channelUserId", channelUserId);
			requestObj.put("senderUserId", senderUserId);
			TextMessage request = session.createTextMessage();
			request.setText(requestObj.toString());
			producer.send(salmonContentQueue, request);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * net.blogracy.controller.CommentsController#onMessage(javax.jms.Message)
	 */
	@Override
	public void onMessage(Message request) {
		TextMessage textRequest = (TextMessage) request;
		String text;
		try {
			text = textRequest.getText();

			JSONObject record = new JSONObject(text);
			if (record.has("request"))
				handleRequest(record);
			else if (record.has("bullyMessageType"))
				handleBullyMessage(record);
		} catch (JMSException e1) {
			e1.printStackTrace();
		} catch (JSONException e) {
			e.printStackTrace();
		}
	}

	private void handleBullyMessage(JSONObject record) throws JSONException {
		if (!record.has("bullyMessageType"))
			return;

		if (!record.has("channelUserId"))
			return;

		String messageType = record.getString("bullyMessageType");
		if (messageType.equalsIgnoreCase("election")) {
			String channelUserId = record.getString("channelUserId");
			String senderUserId = record.getString("senderUserId");
			addendumController.electionMessageReceived(channelUserId, senderUserId);
		} else if (messageType.equalsIgnoreCase("answer")) {
			String channelUserId = record.getString("channelUserId");
			String senderUserId = record.getString("senderUserId");
			addendumController.answerMessageReceived(channelUserId, senderUserId);
		} else if (messageType.equalsIgnoreCase("coordinator")) {
			String channelUserId = record.getString("channelUserId");
			String senderUserId = record.getString("senderUserId");
			addendumController.coordinatorMessageReceived(channelUserId, senderUserId);
		}
	}

	private void handleRequest(JSONObject record) throws JSONException {
		if (!record.has("request"))
			return;

		String requestType = record.getString("request");

		if (requestType.equalsIgnoreCase("contentListQuery")) {
			if (!record.has("queryUserId"))
				return;

			String queryUserId = record.getString("queryUserId");

			JSONArray content = addendumController.getUserAllContent(queryUserId);

			if (content == null)
				return;
			String currentUserId = Configurations.getUserConfig().getUser().getHash().toString();
			sendContentListResponse(currentUserId, queryUserId, content);
		} else if (requestType.equalsIgnoreCase("contentAcceptedInfo")) {
			if (!record.has("contentUserId"))
				return;

			String contentUserId = record.getString("contentUserId");

			String contentId = record.getString("contentId");

			addendumController.removeUserContent(contentUserId, contentId);
		} else if (requestType.equalsIgnoreCase("contentRejectedInfo")) {
			if (!record.has("contentUserId"))
				return;

			String contentUserId = record.getString("contentUserId");

			String contentId = record.getString("contentId");

			addendumController.removeUserContent(contentUserId, contentId);
		} else if (requestType.equalsIgnoreCase("contentReceived")) {
			if (!record.has("senderUserId"))
				return;

			if (!record.has("contentRecipientUserId"))
				return;

			String contentRecipientUserId = record.getString("contentRecipientUserId");
			JSONObject newContentData = record.getJSONObject("contentData");
			String contentId = record.getString("contentId");
			String senderUserId = record.getString("senderUserId");

			addendumController.addUserContent(contentRecipientUserId, contentId, newContentData);

			// If I'm the current delegate for the content's recipient user; I
			// must approve or reject the message
			String currentUserId = Configurations.getUserConfig().getUser().getHash().toString();
			if (currentUserId.compareToIgnoreCase(addendumController.getCurrentDelegate(contentRecipientUserId)) == 0) {
				acceptOrRejectContent(contentRecipientUserId, newContentData, contentId, senderUserId, currentUserId);
			}
		} else if (requestType.equalsIgnoreCase("contentListReceived")) {

			JSONObject contentData = record.optJSONObject("contentData");

			if (contentData == null)
				return;

			String contentDataUserId = contentData.getString("contentUserId");
			String currentUserId = Configurations.getUserConfig().getUser().getHash().toString();

			// if (currentUserId.compareToIgnoreCase(contentDataUserId) == 0) {
			if (currentUserId.compareToIgnoreCase(addendumController.getCurrentDelegate(contentDataUserId)) == 0) {
				JSONArray contents = contentData.optJSONArray("contents");

				if (contents == null)
					return;

				for (int i = 0; i < contents.length(); ++i) {
					JSONObject content = contents.getJSONObject(i).getJSONObject("content");
					String contentId = content.getJSONObject("object").getString("id");
					String contentSenderUserId = content.optJSONObject("actor") == null ? null : content.getJSONObject("actor").getString("id");

					acceptOrRejectContent(contentDataUserId, content, contentId, contentSenderUserId, currentUserId);
				}
			}

		}

	}

	private void acceptOrRejectContent(String contentRecipientUserId, JSONObject newContentData, String contentId, String senderUserId, String currentUserId) {
		if (this.verifyComment(newContentData, senderUserId, contentRecipientUserId)) {
			// Send Accepted message
			this.acceptContent(currentUserId, contentId);
			// add the message itself

			ActivityEntry entry = (ActivityEntry) CONVERTER.convertToObject(newContentData, ActivityEntry.class);
			addendumController.addAddendumEntry(contentRecipientUserId, entry);
		} else {
			// Send Rejected message
			this.rejectContent(currentUserId, contentId);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * net.blogracy.controller.CommentsController#verifyComment(org.json.JSONObject
	 * , java.lang.String, java.lang.String)
	 */
	@Override
	public boolean verifyComment(JSONObject contentData, String senderUserId, String contentRecipientUserId) {
		return true;
	}

}
