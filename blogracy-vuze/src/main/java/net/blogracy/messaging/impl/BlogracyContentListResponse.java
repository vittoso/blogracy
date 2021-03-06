package net.blogracy.messaging.impl;

import java.nio.ByteBuffer;
import java.util.Map;

import net.blogracy.messaging.MessagingManager;

import org.gudy.azureus2.plugins.messaging.Message;
import org.gudy.azureus2.plugins.messaging.MessageException;

public class BlogracyContentListResponse extends BlogracyDataMessageBase {

	public static final String ID = "ID_BLOGRACYMESSAGE_BlogracyContentListResponse";

	public BlogracyContentListResponse(String senderUserId, byte[] senderID, String queryUserId, int hops, String content) {
		super(senderUserId, senderID, hops, content);
		this.queryUserId = queryUserId;
		generateBuffer(generateMessageMap());
	}

	@Override
	public String getID() {
		return ID;
	}

	protected String queryUserId;

	/**
	 * @return the queryUserId
	 */
	public String getQueryUserId() {
		return queryUserId;
	}

	
	/*
	 * (non-Javadoc)
	 * 
	 * @see net.blogracy.messaging.impl.BlogracyDataMessageBase#create(java.nio.
	 * ByteBuffer)
	 */
	@Override
	public Message create(ByteBuffer data) throws MessageException {
		if (data == null) {
			throw new MessageException("[" + getID() + ":" + getVersion() + "] decode error: data == null");
		}

		if (data.remaining() < 13) {/* nothing */
		}
		int size = data.remaining();

		byte[] bMessage = new byte[size];
		data.get(bMessage);

		try {

			@SuppressWarnings("rawtypes")
			Map mMessage = MessagingManager.bDecode(bMessage);
			int messageID = ((Long) mMessage.get("id")).intValue();
			byte[] senderID = (byte[]) mMessage.get("s");
			String uid = new String((byte[]) mMessage.get("uid"));
			int hops = ((Long) mMessage.get("h")).intValue();
			String content = new String((byte[]) mMessage.get("t"));
			String queryUserId = new String((byte[]) mMessage.get("cruid"));
			
			BlogracyContentListResponse message = new BlogracyContentListResponse(uid, senderID, queryUserId, hops, content);
			message.setMessageID(messageID);
			return message;
		}

		catch (Exception e) {
			throw new MessageException("[" + getID() + ":" + getVersion() + "] decode error: " + e);
		}
	}

	@SuppressWarnings("rawtypes")
	protected Map generateMessageMap()
	{
		Map mMessage = super.generateMessageMap();
		mMessage.put("cruid", queryUserId);
		return mMessage;
	}

	
	/*
	 * (non-Javadoc)
	 * 
	 * @see net.blogracy.messaging.impl.BlogracyDataMessageBase#copy()
	 */
	@Override
	public BlogracyDataMessageBase copy() {
		BlogracyContentListResponse message = new BlogracyContentListResponse(getSenderUserId(), getSenderPeerID(), getQueryUserId(), getNbHops(), getContent());
		message.setMessageID(this.getMessageID());
		return message;
	}

}
