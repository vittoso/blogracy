<%@ page import="net.blogracy.model.hashes.Hashes" %>
<%@ page import="net.blogracy.model.users.Users" %>
<%@ page import="net.blogracy.controller.FileSharingImpl" %>
<%@ page import="net.blogracy.controller.ChatController" %>
<%@ page import="net.blogracy.config.Configurations" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="org.apache.shindig.social.opensocial.model.Album" %>
<%@ page import="org.apache.shindig.social.opensocial.model.MediaItem" %>
<%
	String userHash = request.getParameter("user");
if (userHash == null || userHash.length() == 0) {
    userHash = Configurations.getUserConfig().getUser().getHash().toString();
} else if (userHash.length() != 32) {
	userHash = Hashes.hash(userHash); // TODO: remove
}
pageContext.setAttribute("loc",  Configurations.getUserConfig().getUser().getHash().toString());
pageContext.setAttribute("rem", userHash);

String loc = Configurations.getUserConfig().getUser().getHash().toString();
ChatController.setLocalUser(loc);
ChatController.setRemoteUser(userHash);
if (! loc.equals(userHash)) {
  ChatController.privateChatting();
  String channel = ChatController.getPrivateChannel();
  pageContext.setAttribute("channel", channel);
}

List<Album> albums= FileSharingImpl.getSingleton().getAlbums(userHash);
Map<String, List<MediaItem>> mediaItemMap = new HashMap<String, List<MediaItem>>();
for (Album a : albums)
{
	mediaItemMap.put(a.getId(), FileSharingImpl.getSingleton().getMediaItemsWithCachedImages(userHash, a.getId()));
}

pageContext.setAttribute("application", "Blogracy");
pageContext.setAttribute("user", Users.newUser(Hashes.fromString(userHash)));
pageContext.setAttribute("feed", FileSharingImpl.getFeed(userHash));
pageContext.setAttribute("friends", Configurations.getUserConfig().getFriends());
pageContext.setAttribute("localUser", Configurations.getUserConfig().getUser());
pageContext.setAttribute("userAlbums", albums);
pageContext.setAttribute("photoMap", mediaItemMap);
%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>

<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="utf-8">
    <title>${application}</title>
    <meta name="description" content="">
    <meta name="author" content="">

    <!-- Le HTML5 shim, for IE6-8 support of HTML elements -->
    <!--[if lt IE 9]>
    <script src="http://html5shim.googlecode.com/svn/trunk/html5.js"></script>
    <![endif]-->

    <!-- Le styles -->
    <link href="/css/bootstrap.css" rel="stylesheet"/>
    <link type="text/css" href="/css/smoothness/jquery-ui-1.9.0.custom.min.css" rel="stylesheet" />
    
    <script type="text/javascript" src="/scripts/jquery-1.8.2.js"></script>
    <script type="text/javascript" src="/scripts/jquery.form.js"></script>
    <script type="text/javascript" src="/scripts/jquery-ui-1.9.0.custom.min.js"></script>
    <script type="text/javascript" src="/scripts/bootstrap-alerts.js"></script>
    <script type="text/javascript" src="/scripts/fancybox/jquery.fancybox.js?v=2.1.2"></script>
    <script type="text/javascript" src="js/amq_jquery_adapter.js"></script>
    <script type="text/javascript" src="js/amq.js"></script>
    <script type="text/javascript" src="js/chat.js"></script>
    <script type="text/javascript">
        channel = "${channel}"
        jQuery(function() {
            org.activemq.Amq.init({ uri: 'amq', logging: true, timeout: 45, clientId:(new Date()).getTime().toString() });
            org.activemq.Chat.init();
        });
    </script>

    
    <script type="text/javascript">
        // wait for the DOM to be loaded
        jQuery(function() {
            jQuery('#message-send').ajaxForm({
                url: '/fileupload',
                clearForm: true,
                type: 'POST',
                success: function() {
                    console.log(arguments);
                },
                error: function(request, status, statusMessage) {
                    var serverSideException = JSON.parse(request.responseText);
                    var errorMessage = '<div class="alert-message block-message error"><a class="close" href="#">x</a>' +
                                       '<p><strong>' + serverSideException.errorMessage + '</strong></p>' +
                                        '<pre>' + serverSideException.errorTrace.join("\n") + '</pre>' +
                                       '</div>';
                    jQuery(errorPlace).html(errorMessage);
                    jQuery(".alert-message").alert();
                }
                
            });
        });
    
    </script>
	
	<script type="text/javascript" src="/scripts/blogracy-userGalleryHelper.js"></script>
    <script type="text/javascript" src="/scripts/blogracy-mediaThumbnailViewer.js"></script>
	
    <style type="text/css">
            /* Override some defaults */
        html, body {
            background-color: #eee;
        }

        body {
            padding-top: 40px; /* 40px to make the container go all the way to the bottom of the topbar */
        }

        .container > footer p {
            text-align: center; /* center align it with the container */
        }

        .container {
            width: 820px; /* downsize our container to make the content feel a bit tighter and more cohesive.
                           * NOTE: this removes two full columns from the grid, meaning you only go to
                           * 14 columns and not 16.
                           */
        }

            /* The white background content wrapper */
        .content {
            background-color: #fff;
            padding: 20px;
            margin: 0 -20px; /* negative indent the amount of the padding to maintain the grid system */
            -webkit-border-radius: 0 0 6px 6px;
            -moz-border-radius: 0 0 6px 6px;
            border-radius: 0 0 6px 6px;
            -webkit-box-shadow: 0 1px 2px rgba(0, 0, 0, .15);
            -moz-box-shadow: 0 1px 2px rgba(0, 0, 0, .15);
            box-shadow: 0 1px 2px rgba(0, 0, 0, .15);
        }

            /* Page header tweaks */
        .page-header {
            background-color: #f5f5f5;
            padding: 20px 20px 10px;
            margin: -20px -20px 20px;
        }

            /* Styles you shouldn't keep as they are for displaying this base example only */
        .content .span10,
        .content .span4 {
            min-height: 500px;
        }

            /* Give a quick and non-cross-browser friendly divider */
        .content .span4 {
            margin-left: 0;
            padding-left: 19px;
            border-left: 1px solid #eee;
        }

        .topbar .btn {
            border: 0;
        }
        
         .blogracy-thumbnail {
			max-width: 80px;
			max-height: 80px;
		}
		
		.set {
			padding-top:10px;
			clear:both;
		}
		
		.blogracyUserGalleries {
			padding-left:20px;
		}
		
		.imageRow {
			margin-bottom: 20px;
		}
		
		.imageRowHeader {
			width:100%;
			margin:3px;
		}
		
		.blogracyGalleryTitle
		{
			float:left;
		}
		
		.blogracyGalleryTitle p {
			font-weight: bold; 
			font-size: 15px;
		}
    </style>

    <!-- Le fav and touch icons -->
    <link rel="shortcut icon" href="images/favicon.ico">
    <link rel="apple-touch-icon" href="images/apple-touch-icon.png">
    <link rel="apple-touch-icon" sizes="72x72" href="images/apple-touch-icon-72x72.png">
    <link rel="apple-touch-icon" sizes="114x114" href="images/apple-touch-icon-114x114.png">
</head>

<body>

<div class="topbar">
    <div class="fill">
        <div class="container">
            <a class="brand" href="#">${application}</a>
            <ul class="nav">
                <li class="active"><a href="#">Home</a></li>
                <li><a href="#about">About</a></li>
                <li><a href="#contact">Contact</a></li>
            </ul>
        </div>
    </div>
</div>

<div class="container">

    <div class="content">
        <div class="page-header">
            <h1>${user.localNick}
                <small>(hardcoded email)</small>
            </h1>
        </div>
        <div class="row">
            <div id="errorPlace">

            </div>
        </div>
        <div class="row">

            <div class="span10">
                <h2>Messages</h2>

                <form class="span10" id="message-send">
                    <input type="hidden" name="user" value="${user.hash}" />
                    <fieldset class="form-stacked">
                        <div class="clearfix">
                            <label for="messageArea">Send a new message</label>
                            <div class="input">
                                <textarea class="xxlarge" name="usertext" id="messageArea" rows="3"></textarea>
                            </div>
                        </div>
                    </fieldset>
                    <fieldset class="form-stacked">
                        <div class="clearfix">
                            <label for="fileArea">Share a new file</label>
                            <div class="input">
                                <input class="xylarge" name="userfile" id="fileArea" type="file" />
                            </div>
                        </div>
                    </fieldset>
                    <fieldset>
                        <div class="actions">
                            <input type="submit" class="btn primary" value="Send message">&nbsp;
                            <button type="reset" class="btn">Cancel</button>
                        </div>
                    </fieldset>
                </form>
                <div class="span10" id="user-feed">
                <h2>User Feed</h2>
                    <ul>
					<c:forEach var="entry" items="${feed}">
						<li>${entry.content}</li>
					</c:forEach>
					</ul>
                </div>
                    <div class="span10" id="user-galleries">
                   <h2>Photo Galleries</h2>
                   	<c:if test="${localUser == user}"> 
						<form id="create-gallery">
		                    <input type="hidden"name="user" value="${user.hash}" >
		                    <fieldset class="form-stacked">
		                        <div class="clearfix">
		                            <label for="messageArea">Create a new gallery</label>
		                            <div class="input">
		                                <input id="galleryNameTxt" name="galleryname" class="text">
		                                <input type="submit" value="Create Gallery" class="btn primary">&nbsp;
		                                <button class="btn" type="reset">Cancel</button>
		                            </div>
		                        </div>
		                    </fieldset>
		                </form>
	                </c:if>
                  <div class="blogracyUserGalleries">
                     <c:forEach var="album" items="${userAlbums}">
						<div class="imageRow"> 
						<div class="imageRowHeader">
						<div class="blogracyGalleryTitle"> 
						<p>${album.title}</p> 
						</div>  
						<c:if test="${localUser == user}"> 
							<div id="pop"  style="display:none;"></div>
							<div style='float:right'>
							<button  class="btn primary"  type="submit" id="imageUploadOpener" onclick="blogracyGalleryHelper.openDialogWithLink('/imageGallery.jsp?albumId=${album.id}&user=${user.hash}');">Add Images to Gallery</button>
							</div> 
						</c:if>
						</div>
						<div class="set">
						<c:forEach var="mapEntry" items="${photoMap[album.id]}">
					  		  <a href="mediaViewer.jsp?uid=${user.hash}&aid=${album.id}&mid=${mapEntry.id}" rel="${album.id}" title="${mapEntry.title}" class="blogracyMediaThumbnail"><img class="blogracy-thumbnail" src="${mapEntry.url}"/></a>
					  	</c:forEach>
					  	</div>
					  </div>
					</c:forEach>
                    
                  </div>
                </div>
                
<div id="chatroom">
    <div id="chat"></div>

    <div id="members"></div>

    <div id="input">
        <div id="join" class="hidden">
            Username:&nbsp;
            <input id="username" type="text"/>
            <button id="joinB">Join</button>
        </div>
        <div id="joined" class="hidden">
            Chat:&nbsp;
            <input id="phrase" type="text" />
            <button id="sendB">Send</button>
            <button id="leaveB">Leave</button>
        </div>
    </div>
</div>

            </div>
            <div class="span4">
                <h3>Local user</h3>
                <ul>
					<li><a href="/user.jsp?user=${localUser.hash}">${localUser.localNick}</a></li>
				</ul>
                
                <h3>Followers</h3>

                <h3>Followees</h3>
                <ul id="user-friends">
				<c:forEach var="friend" items="${friends}">
					<li><a href="/user.jsp?user=${friend.hash}">${friend.localNick}</a></li>
				</c:forEach>
				</ul>
                <h3>Tags</h3>
<br/>
				<h3>Chat</h3>
				<a href='#self' id='openlink' onclick='openChat()' style='font-size: 10px;'>Chat with ${user.localNick}</a>
            </div>
        </div>
      
    </div>

    <footer>
        <p>&copy; University of Parma 2011</p>
    </footer>



</div>
<!-- /container -->
<div class="span10" id="chatframe" style="width:310px; position:fixed; bottom:0; left:0;">	
	<div id="chathead" style="text-align: left;"> 
		<a href='#self' id="chatlink" onclick='closeChat()'> </a>
	</div>
	<div id="chatbody"> </div>
</div>

<div id="localUser" title="${loc}"></div>
<div id="remoteUser" title="${rem}"></div>
<div id="localNick" title="${localUser.localNick}"></div>

</body>
</html>
