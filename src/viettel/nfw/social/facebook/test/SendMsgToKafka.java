/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package viettel.nfw.social.facebook.test;

import java.util.ArrayList;
import java.util.List;
import java.util.Date;
import javax.management.timer.Timer;
import viettel.gfw.vn.producer.producer.MessageInfo;
import viettel.gfw.vn.producer.producer.ProducerORMWeb;
import viettel.gfw.vn.social.FaceBookProto;
import viettel.gfw.vn.social.FaceBookProtoTrans;
import viettel.nfw.social.model.facebook.Comment;
import viettel.nfw.social.model.facebook.FacebookObject;
import viettel.nfw.social.model.facebook.Like;
import viettel.nfw.social.model.facebook.Post;
import viettel.nfw.social.model.facebook.Profile;

/**
 *
 * @author minhht1
 */
public class SendMsgToKafka {

	private static ProducerORMWeb producer = new ProducerORMWeb("orm_web");

	public static void main(String[] args) throws InterruptedException {
		FacebookObject fbObj = new FacebookObject();
		Comment comment = new Comment();
		comment.setActorProfileDisplayName("MinhHT1");
		comment.setActorProfileId("108536");
		comment.setAttachUrl("https://www.google.com.vn/?gws_rd=ssl#q=An+Kh%C3%A1nh+ho%C3%A0i+%C4%91%E1%BB%A9c");
		comment.setContent("Đem thằng Nguyễn Tấn Dũng ra bắn bỏ! Tổ cha nó!");
		comment.setCreateTime(new Date());
		comment.setId("999999");
		comment.setPostId("111111");
		comment.setUpdateTime(new Date());
		List<Comment> comments = new ArrayList<>();
		comments.add(comment);
		fbObj.setComments(comments);
		fbObj.setLikes(new ArrayList<Like>());

		Post post = new Post();
		post.setActorProfileDisplayName("Minh");
		post.setActorProfileId("108536");
		post.setCommentsCount(100);
		post.setContent("Trâu già sao còn sợ dao phay vậy ông Hương ?\\nCâu hát vang lên bỗng tắt nửa chừng là sao vậy ông Hương ? Phải chăng sân khấu bị cúp điện nên câu hát của ông bị t...ắt nửa chừng ?\\nTrước tiên, tôi hoan hô ông đã mở miệng không như những con Cóc khác chúng cứ mãi ngậm đồng vàng nên chẳng thể hé miệng gọi mưa.\\nNhưng ông bà xưa đã dạy, cục cứt có đầu có đuôi, nói năng phải rõ cả đuôi lẫn đầu. Ông khôn hay ông ăn gian mà chỉ nhắm phần ngọn phang tới còn phần gốc bỏ cho ai đây. Đây là vấn đề của cái đảng nhà ông, nếu ông cho rằng mình phải có trách nhiệm để lên tiếng thì xin ông hãy nói rõ ngọn ngành, đừng chơi trò \\\" bác nông dân và con gấu \\\" để bịp bợm dân tình ông nhé.\\nTôi xin phép hỏi ông :\\n1. Ai bồng Vũ Huy Hoàng lên ghế bộ trưởng bộ công thương vậy ?\\n2. Thời Nông Đức Mạnh làm tổng bí thư, Nguyễn Phú Trọng làm chủ tịch quốc hội, Nguyễn Tấn Dũng làm thủ tướng cho đến lúc Trọng lên tổng bí thư, Hùng hói lên chủ tịch quốc hội. Mỗi lần đại biểu quốc hội chất vấn, Hoàng nhà ông nó ngạo mạn thế nào chắc ông đã rõ.\\nVậy ông có biết ai đã chống lưng cho Hoàng chứ ? Tổng bí thư, chủ tịch quốc hội, chủ tịch nước hay thủ tướng ? Chắc chắn là cả bè lũ 4 tên này đã chống lưng cộng thêm cái đám ủy viên bộ chính trị nữa đúng không ông Hương.\\nVậy ông đề nghị phải xử lý cả tên Hoàng vì đã cất nhắc tên Thanh lên những chỗ ngồi béo bở thì tại sao ông không đặt ra vấn đề xử lý luôn cả những tên trùm sò đã cất nhắc và che chở cho tên Hoàng này ?\\nHay ông sợ Phú Trọng sẽ trù dập ông nếu ông dám phạm vào vùng cấm \\\" xấu che tốt khoe, chống tham nhũng là tự vả vào mồm \\\". Trâu già sao còn sợ dao phay vậy ông Hương ?");
		post.setCreateTime(new Date());
		post.setId("222222");
		post.setUpdateTime(new Date());
		post.setUrl("https://facebook.com/222222");
		post.setWallProfileId("108536");
		post.setType("Group");
		post.setPostTime(new Date());
		List<Post> posts = new ArrayList<>();
		fbObj.setPosts(posts);
		Profile info = new Profile();
		info.setId("108536");
		info.setUrl("http://facebook.com/108536");
		info.setUsername("MinhHT1");
		fbObj.setInfo(info);
		MessageInfo message = new MessageInfo();
		FaceBookProto.FBSocialObject fbSocialObject = FaceBookProtoTrans.facebookObjectTo(fbObj);
		message.setDataSocial(fbSocialObject, MessageInfo.MESSAGE_TYPE_SOCIAL_FACEOOK);
		System.out.println("Sent Object1 to kafka");
		producer.sendMessageORMWeb(message);
		message = new MessageInfo();
		FaceBookProto.FBSocialObject fbSocialObject2 = FaceBookProtoTrans.facebookObjectTo(fbObj);
		message.setDataSocial(fbSocialObject2, MessageInfo.MESSAGE_TYPE_SOCIAL_FACEOOK);
		System.out.println("Sent Object2 to kafka");
		producer.sendMessageORMWeb(message);
		System.out.println("Sleep in 5 minute");
		Thread.sleep(5 * Timer.ONE_MINUTE);
		producer.stopSendMessage();
		System.out.println("END");
	}
}
