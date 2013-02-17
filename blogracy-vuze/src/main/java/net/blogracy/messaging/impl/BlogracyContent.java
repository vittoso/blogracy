package net.blogracy.messaging.impl;

public class BlogracyContent extends BlogracyDataMessageBase {
	public BlogracyContent(String senderUserId, byte[] senderID,
			int hops, String content) {
		super(senderUserId, senderID, hops, content);
	}

	public static final String ID = "ID_BLOGRACYMESSAGE_BlogracyContent";
	
	@Override
	public String getID() {
		return ID;
	}
	
}
