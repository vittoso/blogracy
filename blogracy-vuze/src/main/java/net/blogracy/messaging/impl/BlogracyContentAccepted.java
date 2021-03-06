package net.blogracy.messaging.impl;

import java.nio.ByteBuffer;
import java.util.Map;

import net.blogracy.messaging.MessagingManager;

import org.gudy.azureus2.plugins.messaging.Message;
import org.gudy.azureus2.plugins.messaging.MessageException;

public class BlogracyContentAccepted extends BlogracyDataMessageBase {

	public BlogracyContentAccepted(String senderUserId, byte[] senderID, String contentRecipientUserId,  int hops, String content) {
		super(senderUserId, senderID,  hops, content);
		this.contentRecipientUserId = contentRecipientUserId;
		generateBuffer(generateMessageMap());
	}

	
	public static final String ID = "ID_BLOGRACYMESSAGE_BlogracyContentAccepted";
	
	@Override
	public String getID() {
		return ID;
	}
	
	
	protected String contentRecipientUserId;

	/**
	 * @return the contentRecipientUserId
	 */
	public String getContentRecipientUserId() {
		return contentRecipientUserId;
	}

	
	
	/* (non-Javadoc)
	 * @see net.blogracy.messaging.impl.BlogracyDataMessageBase#create(java.nio.ByteBuffer)
	 */
	@Override
	public Message create(ByteBuffer data) throws MessageException {
		if(data == null) {
			throw new MessageException( "[" +getID() + ":" +getVersion()+ "] decode error: data == null" );
		}

		if(data.remaining() < 13) {/*nothing*/}
		int size = data.remaining();

		byte[] bMessage = new byte[size];
		data.get(bMessage);

		try {

			@SuppressWarnings("rawtypes")
			Map mMessage = MessagingManager.bDecode(bMessage);
			int messageID = ((Long)mMessage.get("id")).intValue();
			byte[] senderID = (byte[])mMessage.get("s");
			String uid = new String((byte[])mMessage.get("uid"));
			int hops = ((Long)mMessage.get("h")).intValue();
			String content = new String((byte[])mMessage.get("t"));
			String contentRecipientUserId = new String((byte[]) mMessage.get("cruid"));
			
			BlogracyContentAccepted message = new BlogracyContentAccepted(uid, senderID, contentRecipientUserId, hops, content);
			message.setMessageID(messageID);
			return message;
		} 

		catch(Exception e) {
			throw new MessageException( "[" +getID() + ":" +getVersion()+ "] decode error: " + e );
		}
	}


	@SuppressWarnings("rawtypes")
	protected Map generateMessageMap()
	{
		Map mMessage = super.generateMessageMap();
		mMessage.put("cruid", contentRecipientUserId);
		return mMessage;
	}


	/* (non-Javadoc)
	 * @see net.blogracy.messaging.impl.BlogracyDataMessageBase#copy()
	 */
	@Override
	public BlogracyDataMessageBase copy() {
		BlogracyContentAccepted message = new BlogracyContentAccepted(getSenderUserId(), getSenderPeerID(),  getContentRecipientUserId(), getNbHops(), getContent());
		message.setMessageID(this.getMessageID());
		return message;
	}
}
