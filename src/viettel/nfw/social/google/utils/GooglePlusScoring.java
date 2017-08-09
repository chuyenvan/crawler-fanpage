package viettel.nfw.social.google.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.nigma.engine.util.Funcs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import vn.viettel.parser.bigdata.BigDataCommunicator;
import vn.viettel.social.utils.ByteUtils;
import vn.viettel.social.utils.SocialContentDetector;
import vn.viettel.social.utils.SocialContentDetectorType;
import vn.viettel.social.utils.bigdata.DataResponseHandler;
import vn.viettel.social.utils.bigdata.SColumn;
import vn.viettel.social.utils.bigdata.STable;
import vn.viettel.social.utils.consts.SCommon;

import com.google.common.util.concurrent.AtomicDouble;

public class GooglePlusScoring {

    private static final Logger LOG = LoggerFactory.getLogger(GooglePlusScoring.class);

    private BigDataCommunicator bigDataCommunicator;
    private String profileId;
    private AtomicDouble pvscore = new AtomicDouble(0.0);
    private AtomicDouble pescore = new AtomicDouble(0.0);
    private AtomicDouble pownerSensitiveScore = new AtomicDouble(0.0);
    private AtomicDouble potherSensitiveScore = new AtomicDouble(0.0);

    public GooglePlusScoring(BigDataCommunicator bigDataCommunicator, String profileId) {
        this.bigDataCommunicator = bigDataCommunicator;
        this.profileId = profileId;
    }

    private static final long TIME_OUT = 5 * 1000L;

    public void doTask() {
        processUserProfile();
        processTimeline();
    }

    public double getProfileVietnameseScore() {
        double score = pvscore.get();
        return score;
    }

    public double getProfileEnglishScore() {
        double score = pescore.get();
        return score;
    }

    public double getProfileOwnerSensitiveScore() {
        double score = pownerSensitiveScore.get();
        return score;
    }

    public double getProfileOtherSensitiveScore() {
        double score = potherSensitiveScore.get();
        return score;
    }

    private void processUserProfile() {
        String tableName = STable.GP_PERSON;
        byte[] key = profileId.getBytes();
        int msgId = bigDataCommunicator.getAllData(tableName, key);
        DataResponseHandler handler = (DataResponseHandler) bigDataCommunicator.getResponseHandler();
        HashMap<byte[], byte[]> profileData = handler.waitAndGetAllValues(msgId, TIME_OUT);
        if (!MapUtils.isEmpty(profileData)) {
            parseUserData(profileData);
        }
    }

    private void parseUserData(HashMap<byte[], byte[]> profileData) {
        for (Map.Entry<byte[], byte[]> item : profileData.entrySet()) {
            String key = new String(item.getKey());
            if (StringUtils.equalsIgnoreCase(key, new String(SColumn.GpPerson.CURRENT_LOCATION_FLAG))) {
                int flagValue = ByteUtils.byteArrayToInt(item.getValue());
                List<SocialContentDetectorType> listFlags = SocialContentDetector.reverseFlag(flagValue, SCommon.CONTENT_OTHER);
                calculate(listFlags);
            }
        }
    }

    private boolean processTimeline() {
        String tableName = STable.GP_ACTIVITY;
        int msgId = bigDataCommunicator.getAllPostsByOwnerId(tableName, profileId);
        DataResponseHandler handler = (DataResponseHandler) bigDataCommunicator.getResponseHandler();
        HashMap<byte[], byte[]> mapIds = handler.waitAndGetAllValues(msgId, TIME_OUT);
        if (!MapUtils.isEmpty(mapIds)) {
            List<String> postIdsList = new ArrayList<String>();
            for (byte[] id : mapIds.keySet()) {
                postIdsList.add(new String(id));
            }
            boolean result = processPosts(postIdsList);
            return result;
        } else {
            return false;
        }
    }

    private boolean processPosts(List<String> postIdsList) {

        ThreadPoolExecutor excutor = new ThreadPoolExecutor(10, 10, 1, TimeUnit.DAYS,
                new java.util.concurrent.ArrayBlockingQueue<Runnable>(1000));
        for (String postId : postIdsList) {
            try {
                excutor.execute(new RunnablePostImpl(postId, bigDataCommunicator));
            } catch (RejectedExecutionException ex) {
                while (excutor.getQueue().size() > 100) {
                    Funcs.sleep(50);
                }
            }
        }
        excutor.shutdown();
        try {
            boolean finished = excutor.awaitTermination(40 * 1000L, TimeUnit.MILLISECONDS);
            return finished;
        } catch (InterruptedException e) {
            LOG.error(e.getMessage(), e);
        }
        return false;
    }

    private class RunnablePostImpl implements Runnable {

        private final String postId;
        private BigDataCommunicator runablePostCommunicator;

        private AtomicDouble postownerSensitiveScore = new AtomicDouble(0.0);
        private AtomicDouble postotherSensitiveScore = new AtomicDouble(0.0);

        public RunnablePostImpl(String postId, BigDataCommunicator communicator) {
            this.postId = postId;
            this.runablePostCommunicator = communicator;
        }

        @Override
        public void run() {
            Thread.currentThread().setName("RunnableImpl." + postId);
            String tableName = STable.GP_ACTIVITY;

            byte[] postIdKey = postId.getBytes();
            int msgId = runablePostCommunicator.getAllData(tableName, postIdKey);
            DataResponseHandler handler = (DataResponseHandler) runablePostCommunicator.getResponseHandler();
            HashMap<byte[], byte[]> postData = handler.waitAndGetAllValues(msgId, TIME_OUT);
            if (!MapUtils.isEmpty(postData)) {
                parsePostData(postData);
            }

            // comments
            int msgIdComment = runablePostCommunicator.getAllCommentsByPostId(tableName, postId);
            DataResponseHandler handlerComments = (DataResponseHandler) runablePostCommunicator.getResponseHandler();
            HashMap<byte[], byte[]> mapCommentIds = handlerComments.waitAndGetAllValues(msgIdComment, TIME_OUT);
            if (!MapUtils.isEmpty(mapCommentIds)) {
                List<String> commentIdsList = new ArrayList<>();
                for (byte[] id : mapCommentIds.keySet()) {
                    commentIdsList.add(new String(id));
                }
                boolean result = processComments(commentIdsList);
                if (result) {
                    // TODO add to bigdata
                    double ownersenscore = postownerSensitiveScore.get();
                    double othersenscore = postotherSensitiveScore.get();
                    LOG.info("Scores of post {} are: ownerS {} - otherS {}", new Object[]{postId, ownersenscore, othersenscore});
                    if (StringUtils.isNotEmpty(postId)) {
                        runablePostCommunicator.setGoolgePlusActivity(postIdKey, SColumn.GPActivity.OWNER_SENSITIVE_SCORE,
                                ByteUtils.doubleToByteArray(ownersenscore), 0);
                        runablePostCommunicator.setGoolgePlusActivity(postIdKey, SColumn.GPActivity.OTHER_SENSITIVE_SCORE,
                                ByteUtils.doubleToByteArray(othersenscore), 0);
                    }
                }
            }

        }

        private void parsePostData(HashMap<byte[], byte[]> postData) {
            for (Map.Entry<byte[], byte[]> item : postData.entrySet()) {
                String key = new String(item.getKey());
                if (StringUtils.equalsIgnoreCase(key, new String(SColumn.GPActivity.CONTENT_FLAG))) {
                    // int type
                    int flagValue = ByteUtils.byteArrayToInt(item.getValue());
                    List<SocialContentDetectorType> listFlags = SocialContentDetector.reverseFlag(flagValue, SCommon.CONTENT_POST);
                    calculate(listFlags);
                    calculateForPost(listFlags);
                }
            }
        }

        private boolean processComments(List<String> commentIdsList) {

            ThreadPoolExecutor excutor = new ThreadPoolExecutor(10, 10, 1, TimeUnit.DAYS,
                    new java.util.concurrent.ArrayBlockingQueue<Runnable>(1000));
            for (String commentId : commentIdsList) {
                try {
                    excutor.execute(new RunnableCommentsImpl(commentId, runablePostCommunicator));
                } catch (RejectedExecutionException ex) {
                    while (excutor.getQueue().size() > 100) {
                        Funcs.sleep(50);
                    }
                }
            }
            excutor.shutdown();
            try {
                boolean finished = excutor.awaitTermination(40 * 1000L, TimeUnit.MILLISECONDS);
                return finished;
            } catch (InterruptedException e) {
                LOG.error(e.getMessage(), e);
            }
            return false;
        }

        private class RunnableCommentsImpl implements Runnable {

            private final String commentId;
            private BigDataCommunicator runableCommentCommunicator;

            public RunnableCommentsImpl(String commentId, BigDataCommunicator communicator) {
                this.commentId = commentId;
                this.runableCommentCommunicator = communicator;
            }

            @Override
            public void run() {
                Thread.currentThread().setName("RunnableCommentsImpl." + commentId);
                String tableName = STable.GP_COMENT;

                byte[] commentIdKey = commentId.getBytes();
                int msgId = runableCommentCommunicator.getAllData(tableName, commentIdKey);
                DataResponseHandler handler = (DataResponseHandler) runableCommentCommunicator.getResponseHandler();
                HashMap<byte[], byte[]> commentData = handler.waitAndGetAllValues(msgId, TIME_OUT);
                if (!MapUtils.isEmpty(commentData)) {
                    parseCommentData(commentData);
                }
            }

            private void parseCommentData(HashMap<byte[], byte[]> commentData) {

                for (Map.Entry<byte[], byte[]> item : commentData.entrySet()) {
                    String key = new String(item.getKey());
                    if (StringUtils.equalsIgnoreCase(key, new String(SColumn.GpComment.CONTENT_FLAG))) {
                        // int type
                        int flagValue = ByteUtils.byteArrayToInt(item.getValue());
                        List<SocialContentDetectorType> listFlags = SocialContentDetector.reverseFlag(flagValue, SCommon.CONTENT_COMMENT);
                        calculate(listFlags);
                        calculateForPost(listFlags);
                    }
                }
            }
        }

        private void calculateForPost(List<SocialContentDetectorType> listFlags) {
            for (SocialContentDetectorType type : listFlags) {
                switch (type) {
                    case CONTENT_REACTION_FROM_OWNER:
                        postownerSensitiveScore.addAndGet(type.getScore());
                        break;
                    case CONTENT_REACTION_FROM_OTHER:
                        postotherSensitiveScore.addAndGet(type.getScore());
                        break;
                    case POST_REACTION_FROM_OWNER:
                        postownerSensitiveScore.addAndGet(type.getScore());
                        break;
                    case POST_REACTION_FROM_OTHER:
                        postotherSensitiveScore.addAndGet(type.getScore());
                        break;
                    case COMMENT_REACTION_FROM_OWNER:
                        postownerSensitiveScore.addAndGet(type.getScore());
                        break;
                    case COMMENT_REACTION_FROM_OTHER:
                        postotherSensitiveScore.addAndGet(type.getScore());
                        break;
                    default:
                        break;
                }
            }
        }
    }

    private void calculate(List<SocialContentDetectorType> listFlags) {
        for (SocialContentDetectorType type : listFlags) {
            switch (type) {
                case LANGUAGE_VIETNAMESE_FROM_OWNER:
                    pvscore.addAndGet(type.getScore());
                    break;
                case LANGUAGE_VIETNAMESE_FROM_OTHER:
                    pvscore.addAndGet(type.getScore());
                    break;
                case LANGUAGE_ENGLISH_FROM_OWNER:
                    pescore.addAndGet(type.getScore());
                    break;
                case LANGUAGE_ENGLISH_FROM_OTHER:
                    pescore.addAndGet(type.getScore());
                    break;
                case COUNTRY_VIETNAM:
                    pvscore.addAndGet(type.getScore());
                    break;
                case COUNTRY_OTHER:
                    pvscore.addAndGet(type.getScore());
                    break;
                case CONTENT_REACTION_FROM_OWNER:
                    pownerSensitiveScore.addAndGet(type.getScore());
                    break;
                case CONTENT_REACTION_FROM_OTHER:
                    potherSensitiveScore.addAndGet(type.getScore());
                    break;
                case POST_REACTION_FROM_OWNER:
                    pownerSensitiveScore.addAndGet(type.getScore());
                    break;
                case POST_REACTION_FROM_OTHER:
                    potherSensitiveScore.addAndGet(type.getScore());
                    break;
                case COMMENT_REACTION_FROM_OWNER:
                    pownerSensitiveScore.addAndGet(type.getScore());
                    break;
                case COMMENT_REACTION_FROM_OTHER:
                    potherSensitiveScore.addAndGet(type.getScore());
                    break;
                default:
                    break;
            }
        }
    }

//	public static void main(String[] args) {
//
//		String tableName = "";
//		long timeout = TIME_OUT;
//		tableName = STable.GP_PERSON;
//		timeout = 30 * 1000L;
//		BigDataCommunicator bigData = new BigDataCommunicator(false);
//		bigData.setResponseHandler(new DataResponseHandler(bigData.bigdataInitiator));
//
//		if (StringUtils.isNotEmpty(tableName)) {
//			int msgId = bigData.getAllKeys(tableName);
//			DataResponseHandler handler = (DataResponseHandler) bigData.getResponseHandler();
//			List<String> listProfileIds = handler.waitAndGetAllKeys(msgId, timeout);
//			if (!listProfileIds.isEmpty()) {
//
//				LOG.info("total googleplus profiles: {}", listProfileIds.size());
//
//				ThreadPoolExecutor excutor = new ThreadPoolExecutor(40, 40, 1, TimeUnit.DAYS,
//						new java.util.concurrent.ArrayBlockingQueue<Runnable>(1000));
//				for (String profileId : listProfileIds) {
//					try {
//						excutor.execute(new RunnableImpl(profileId, bigData));
//					} catch (RejectedExecutionException ex) {
//						while (excutor.getQueue().size() > 100) {
//							Funcs.sleep(50);
//						}
//					}
//				}
//				excutor.shutdown();
//				try {
//					boolean finished = excutor.awaitTermination(10 * 1000L, TimeUnit.MILLISECONDS);
//
//					if (finished) {
//						// TODO
//						LOG.info("DONE");
//					}
//
//				} catch (InterruptedException e) {
//					LOG.error(e.getMessage(), e);
//				}
//			}
//		}
//	}
//	
//	private static class RunnableImpl implements Runnable {
//
//		private final String profileId;
//		private BigDataCommunicator runableCommunicator;
//
//		public RunnableImpl(String profileId, BigDataCommunicator communicator) {
//			this.profileId = profileId;
//			this.runableCommunicator = communicator;
//		}
//
//		@Override
//		public void run() {
//			Thread.currentThread().setName("RunnableImpl." + profileId);
//			GooglePlusScoring gpScoring = new GooglePlusScoring(runableCommunicator, profileId);
//			gpScoring.doTask();
//			double vscore = gpScoring.getProfileVietnameseScore();
//			double escore = gpScoring.getProfileEnglishScore();
//			double ownerS = gpScoring.getProfileOwnerSensitiveScore();
//			double otherS = gpScoring.getProfileOtherSensitiveScore();
//			LOG.info("Score of profile {} - vscore: {} - escore: {} - owner: {} - other: {}", new Object[] { profileId, vscore, escore, ownerS,
//					otherS });
//			runableCommunicator.setGooglePlusPerson(profileId.getBytes(), SColumn.GpPerson.VIETNAMESE_SCORE, ByteUtils.doubleToByteArray(vscore), 0);
//			runableCommunicator.setGooglePlusPerson(profileId.getBytes(), SColumn.GpPerson.ENGLISH_SCORE, ByteUtils.doubleToByteArray(escore), 0);
//			runableCommunicator.setGooglePlusPerson(profileId.getBytes(), SColumn.GpPerson.OWNER_SENSITIVE_SCORE, ByteUtils.doubleToByteArray(ownerS), 0);
//			runableCommunicator.setGooglePlusPerson(profileId.getBytes(), SColumn.GpPerson.OTHER_SENSITIVE_SCORE, ByteUtils.doubleToByteArray(otherS), 0);
//		}
//	}
}
