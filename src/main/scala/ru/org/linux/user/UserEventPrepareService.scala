package ru.org.linux.user

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import ru.org.linux.group.GroupDao
import ru.org.linux.spring.dao.{DeleteInfoDao, MsgbaseDao}
import ru.org.linux.util.bbcode.LorCodeService

import scala.collection.JavaConverters._

@Service
class UserEventPrepareService @Autowired() (
  msgbaseDao:MsgbaseDao, lorCodeService: LorCodeService, userDao:UserDao, deleteInfoDao:DeleteInfoDao, groupDao:GroupDao
) {
  /**
   * @param events      список событий
   * @param readMessage возвращать ли отрендеренное содержимое уведомлений (используется только для RSS)
   * @param secure      является ли текущие соединение https
   */
  def prepare(events:java.util.List[UserEvent], readMessage:Boolean, secure:Boolean):java.util.List[PreparedUserEvent] = {
    val evts = events.asScala

    val userIds = (evts.map(_.getCommentAuthor) ++ evts.map(_.getTopicAuthor)).filter(_ != 0).distinct

    val users = userDao.getUsersCached(userIds.map(Integer.valueOf).asJavaCollection).asScala.map { user ⇒
      user.getId -> user
    }.toMap

    val prepared = evts map { event ⇒
      val msgid = if (event.isComment) event.getCid else event.getMsgid

      val text = if (readMessage) {
        val messageText = msgbaseDao.getMessageText(msgid)

        Some(lorCodeService.prepareTextRSS(messageText.getText, secure, messageText.isLorcode))
      } else {
        None
      }

      val topicAuthor = users(event.getTopicAuthor)

      val commentAuthor = if (event.isComment) {
        users.get(event.getCommentAuthor)
      } else {
        None
      }

      val bonus = (if ("DEL" == event.getType.getType) {
        Option(deleteInfoDao.getDeleteInfo(msgid))
      } else {
        None
      }) map (_.getBonus)

      PreparedUserEvent(event, text, topicAuthor, commentAuthor, bonus, groupDao.getGroup(event.getGroupId))
    }

    prepared.asJava
  }
}
