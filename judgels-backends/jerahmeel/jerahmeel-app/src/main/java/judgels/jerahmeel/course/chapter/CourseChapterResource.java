package judgels.jerahmeel.course.chapter;

import static com.google.common.base.Preconditions.checkArgument;
import static judgels.service.ServiceUtils.checkAllowed;
import static judgels.service.ServiceUtils.checkFound;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import io.dropwizard.hibernate.UnitOfWork;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Inject;
import judgels.jerahmeel.api.chapter.Chapter;
import judgels.jerahmeel.api.chapter.ChapterInfo;
import judgels.jerahmeel.api.chapter.ChapterProgress;
import judgels.jerahmeel.api.course.chapter.CourseChapter;
import judgels.jerahmeel.api.course.chapter.CourseChapterService;
import judgels.jerahmeel.api.course.chapter.CourseChapterUserProgressesData;
import judgels.jerahmeel.api.course.chapter.CourseChapterUserProgressesResponse;
import judgels.jerahmeel.api.course.chapter.CourseChaptersResponse;
import judgels.jerahmeel.chapter.ChapterStore;
import judgels.jerahmeel.course.CourseStore;
import judgels.jerahmeel.role.RoleChecker;
import judgels.jerahmeel.stats.StatsStore;
import judgels.service.actor.ActorChecker;
import judgels.service.api.actor.AuthHeader;
import judgles.jophiel.user.UserClient;

public class CourseChapterResource implements CourseChapterService {
    private final ActorChecker actorChecker;
    private final RoleChecker roleChecker;
    private final CourseStore courseStore;
    private final CourseChapterStore courseChapterStore;
    private final ChapterStore chapterStore;
    private final StatsStore statsStore;
    private final UserClient userClient;

    @Inject
    public CourseChapterResource(
            ActorChecker actorChecker,
            RoleChecker roleChecker,
            CourseStore courseStore,
            CourseChapterStore courseChapterStore,
            ChapterStore chapterStore,
            StatsStore statsStore,
            UserClient userClient) {

        this.actorChecker = actorChecker;
        this.roleChecker = roleChecker;
        this.courseStore = courseStore;
        this.courseChapterStore = courseChapterStore;
        this.chapterStore = chapterStore;
        this.statsStore = statsStore;
        this.userClient = userClient;
    }

    @Override
    @UnitOfWork
    public void setChapters(AuthHeader authHeader, String courseJid, List<CourseChapter> data) {
        String actorJid = actorChecker.check(authHeader);
        checkFound(courseStore.getCourseByJid(courseJid));
        checkAllowed(roleChecker.isAdmin(actorJid));

        Set<String> aliases = data.stream().map(CourseChapter::getAlias).collect(Collectors.toSet());
        Set<String> chapterJids = data.stream().map(CourseChapter::getChapterJid).collect(Collectors.toSet());

        checkArgument(aliases.size() == data.size(), "Chapter aliases must be unique");
        checkArgument(chapterJids.size() == data.size(), "Chapter JIDs must be unique");

        courseChapterStore.setChapters(courseJid, data);
    }

    @Override
    @UnitOfWork(readOnly = true)
    public CourseChaptersResponse getChapters(Optional<AuthHeader> authHeader, String courseJid) {
        String actorJid = actorChecker.check(authHeader);
        checkFound(courseStore.getCourseByJid(courseJid));

        List<CourseChapter> chapters = courseChapterStore.getChapters(courseJid);
        Set<String> chapterJids = chapters.stream().map(CourseChapter::getChapterJid).collect(Collectors.toSet());
        Map<String, ChapterInfo> chaptersMap = chapterStore.getChapterInfosByJids(chapterJids);
        Map<String, ChapterProgress> chapterProgressesMap = statsStore.getChapterProgressesMap(actorJid, chapterJids);

        return new CourseChaptersResponse.Builder()
                .data(chapters)
                .chaptersMap(chaptersMap)
                .chapterProgressesMap(chapterProgressesMap)
                .build();
    }

    @Override
    @UnitOfWork(readOnly = true)
    public Chapter getChapter(Optional<AuthHeader> authHeader, String courseJid, String chapterAlias) {
        checkFound(courseStore.getCourseByJid(courseJid));

        CourseChapter courseChapter = checkFound(courseChapterStore.getChapterByAlias(courseJid, chapterAlias));
        return checkFound(chapterStore.getChapterByJid(courseChapter.getChapterJid()));
    }

    @Override
    @UnitOfWork(readOnly = true)
    public CourseChapterUserProgressesResponse getChapterUserProgresses(
            Optional<AuthHeader> authHeader,
            String courseJid,
            CourseChapterUserProgressesData data) {

        checkFound(courseStore.getCourseByJid(courseJid));

        checkArgument(data.getUsernames().size() <= 100, "Cannot get more than 100 users.");

        List<CourseChapter> chapters = courseChapterStore.getChapters(courseJid);
        Set<String> chapterJids = chapters.stream().map(CourseChapter::getChapterJid).collect(Collectors.toSet());

        Map<String, Long> totalProblemsMap = statsStore.getChapterTotalProblemsMap(chapterJids);
        List<Integer> totalProblemsList = Lists.transform(chapters,
                chapter -> (int) (long) totalProblemsMap.getOrDefault(chapter.getChapterJid(), 0L));


        Map<String, String> usernameToJidsMap = userClient.translateUsernamesToJids(data.getUsernames());
        Map<String, Map<String, Integer>> userSolvedProblemsMap =
                statsStore.getChapterUserSolvedProblemsMap(
                        ImmutableSet.copyOf(usernameToJidsMap.values()),
                        chapterJids);

        Map<String, List<Integer>> userProgressesMap = new HashMap<>();
        for (String username : data.getUsernames()) {
            if (!usernameToJidsMap.containsKey(username)) {
                continue;
            }
            String userJid = usernameToJidsMap.get(username);
            Map<String, Integer> solvedProblemsMap = userSolvedProblemsMap.getOrDefault(userJid, new HashMap<>());
            List<Integer> progresses = new ArrayList<>();
            for (CourseChapter chapter : chapters) {
                progresses.add(solvedProblemsMap.getOrDefault(chapter.getChapterJid(), 0));
            }
            userProgressesMap.put(username, progresses);
        }

        return new CourseChapterUserProgressesResponse.Builder()
                .totalProblemsList(totalProblemsList)
                .userProgressesMap(userProgressesMap)
                .build();
    }
}
