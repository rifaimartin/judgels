package judgels.uriel.contest.scoreboard;

import static judgels.uriel.api.contest.supervisor.SupervisorPermissionType.SCOREBOARD;
import static org.assertj.core.api.Assertions.assertThat;

import judgels.uriel.api.contest.problem.ContestProblemData;
import judgels.uriel.api.contest.problem.ContestProblemStatus;
import judgels.uriel.api.contest.scoreboard.ContestScoreboardData;
import judgels.uriel.api.contest.scoreboard.ContestScoreboardType;
import judgels.uriel.contest.problem.ContestProblemStore;
import judgels.uriel.contest.role.AbstractRoleCheckerIntegrationTests;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ContestScoreboardRoleCheckerIntegrationTests extends AbstractRoleCheckerIntegrationTests {
    private ContestScoreboardRoleChecker checker;
    private ContestScoreboardStore scoreboardStore;
    private ContestProblemStore problemStore;

    @BeforeEach
    void setUpSession(SessionFactory sessionFactory) {
        prepare(sessionFactory);
        checker = component.contestScoreboardRoleChecker();
        scoreboardStore = component.contestScoreboardStore();
        problemStore = component.contestProblemStore();
    }

    @Test
    void view_default() {
        assertThat(checker.canViewDefault(ADMIN, contestA)).isTrue();
        assertThat(checker.canViewDefault(ADMIN, contestAStarted)).isTrue();
        assertThat(checker.canViewDefault(ADMIN, contestB)).isTrue();
        assertThat(checker.canViewDefault(ADMIN, contestBStarted)).isTrue();
        assertThat(checker.canViewDefault(ADMIN, contestC)).isTrue();

        assertThat(checker.canViewDefault(USER, contestA)).isFalse();
        assertThat(checker.canViewDefault(USER, contestAStarted)).isTrue();
        assertThat(checker.canViewDefault(USER, contestB)).isFalse();
        assertThat(checker.canViewDefault(USER, contestC)).isFalse();

        assertThat(checker.canViewDefault(CONTESTANT, contestA)).isFalse();
        assertThat(checker.canViewDefault(CONTESTANT, contestAStarted)).isTrue();
        assertThat(checker.canViewDefault(CONTESTANT, contestB)).isFalse();
        assertThat(checker.canViewDefault(CONTESTANT, contestBStarted)).isTrue();
        assertThat(checker.canViewDefault(CONTESTANT, contestC)).isFalse();

        assertThat(checker.canViewDefault(SUPERVISOR, contestA)).isFalse();
        assertThat(checker.canViewDefault(SUPERVISOR, contestAStarted)).isTrue();
        assertThat(checker.canViewDefault(SUPERVISOR, contestB)).isFalse();
        assertThat(checker.canViewDefault(SUPERVISOR, contestBStarted)).isTrue();
        assertThat(checker.canViewDefault(SUPERVISOR, contestC)).isFalse();
        addSupervisorToContestBWithPermission(SCOREBOARD);
        assertThat(checker.canViewDefault(SUPERVISOR, contestA)).isFalse();
        assertThat(checker.canViewDefault(SUPERVISOR, contestAStarted)).isTrue();
        assertThat(checker.canViewDefault(SUPERVISOR, contestB)).isTrue();
        assertThat(checker.canViewDefault(SUPERVISOR, contestBStarted)).isTrue();
        assertThat(checker.canViewDefault(SUPERVISOR, contestC)).isFalse();

        assertThat(checker.canViewDefault(MANAGER, contestA)).isFalse();
        assertThat(checker.canViewDefault(MANAGER, contestAStarted)).isTrue();
        assertThat(checker.canViewDefault(MANAGER, contestB)).isTrue();
        assertThat(checker.canViewDefault(MANAGER, contestBStarted)).isTrue();
        assertThat(checker.canViewDefault(MANAGER, contestC)).isFalse();
    }

    @Test
    void view_official_and_frozen() {
        addSupervisorToContestBWithPermission(SCOREBOARD);

        assertThat(checker.canViewOfficialAndFrozen(SUPERVISOR, contestBStarted)).isFalse();

        scoreboardStore.upsertScoreboard(contestBStarted.getJid(), new ContestScoreboardData.Builder()
                .type(ContestScoreboardType.OFFICIAL)
                .scoreboard("official")
                .build());
        assertThat(checker.canViewOfficialAndFrozen(SUPERVISOR, contestB)).isFalse();

        scoreboardStore.upsertScoreboard(contestBStarted.getJid(), new ContestScoreboardData.Builder()
                .type(ContestScoreboardType.FROZEN)
                .scoreboard("frozen")
                .build());
        assertThat(checker.canViewOfficialAndFrozen(SUPERVISOR, contestBStarted)).isTrue();

        assertThat(checker.canViewOfficialAndFrozen(CONTESTANT, contestBStarted)).isFalse();
    }

    @Test
    void view_closed_problems() {
        addSupervisorToContestBWithPermission(SCOREBOARD);

        problemStore.upsertProblem(contestBStarted.getJid(), new ContestProblemData.Builder()
                .problemJid("problemJid1")
                .alias("A")
                .status(ContestProblemStatus.OPEN)
                .submissionsLimit(0)
                .build());

        assertThat(checker.canViewClosedProblems(SUPERVISOR, contestBStarted)).isFalse();

        problemStore.upsertProblem(contestBStarted.getJid(), new ContestProblemData.Builder()
                .problemJid("problemJid2")
                .alias("B")
                .status(ContestProblemStatus.CLOSED)
                .submissionsLimit(0)
                .build());

        assertThat(checker.canViewClosedProblems(SUPERVISOR, contestBStarted)).isTrue();

        assertThat(checker.canViewClosedProblems(CONTESTANT, contestBStarted)).isFalse();
    }

    @Test
    void supervise() {
        assertThat(checker.canSupervise(ADMIN, contestA)).isTrue();
        assertThat(checker.canSupervise(ADMIN, contestAStarted)).isTrue();
        assertThat(checker.canSupervise(ADMIN, contestB)).isTrue();
        assertThat(checker.canSupervise(ADMIN, contestBStarted)).isTrue();
        assertThat(checker.canSupervise(ADMIN, contestC)).isTrue();

        assertThat(checker.canSupervise(USER, contestA)).isFalse();
        assertThat(checker.canSupervise(USER, contestAStarted)).isFalse();
        assertThat(checker.canSupervise(USER, contestB)).isFalse();
        assertThat(checker.canSupervise(USER, contestBStarted)).isFalse();
        assertThat(checker.canSupervise(USER, contestC)).isFalse();

        assertThat(checker.canSupervise(CONTESTANT, contestA)).isFalse();
        assertThat(checker.canSupervise(CONTESTANT, contestAStarted)).isFalse();
        assertThat(checker.canSupervise(CONTESTANT, contestB)).isFalse();
        assertThat(checker.canSupervise(CONTESTANT, contestBStarted)).isFalse();
        assertThat(checker.canSupervise(CONTESTANT, contestC)).isFalse();

        assertThat(checker.canSupervise(SUPERVISOR, contestA)).isFalse();
        assertThat(checker.canSupervise(SUPERVISOR, contestAStarted)).isFalse();
        assertThat(checker.canSupervise(SUPERVISOR, contestB)).isFalse();
        assertThat(checker.canSupervise(SUPERVISOR, contestBStarted)).isFalse();
        assertThat(checker.canSupervise(SUPERVISOR, contestC)).isFalse();
        addSupervisorToContestBWithPermission(SCOREBOARD);
        assertThat(checker.canSupervise(SUPERVISOR, contestA)).isFalse();
        assertThat(checker.canSupervise(SUPERVISOR, contestAStarted)).isFalse();
        assertThat(checker.canSupervise(SUPERVISOR, contestB)).isTrue();
        assertThat(checker.canSupervise(SUPERVISOR, contestBStarted)).isTrue();
        assertThat(checker.canSupervise(SUPERVISOR, contestC)).isFalse();

        assertThat(checker.canSupervise(MANAGER, contestA)).isFalse();
        assertThat(checker.canSupervise(MANAGER, contestAStarted)).isFalse();
        assertThat(checker.canSupervise(MANAGER, contestB)).isTrue();
        assertThat(checker.canSupervise(MANAGER, contestBStarted)).isTrue();
        assertThat(checker.canSupervise(MANAGER, contestC)).isFalse();
    }
}
