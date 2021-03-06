package org.iatoki.judgels.sandalphon.problem.base.version;

import static judgels.service.ServiceUtils.checkAllowed;
import static judgels.service.ServiceUtils.checkFound;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import judgels.jophiel.api.profile.Profile;
import judgels.jophiel.api.profile.ProfileService;
import judgels.sandalphon.api.problem.Problem;
import org.iatoki.judgels.GitCommit;
import org.iatoki.judgels.play.template.HtmlTemplate;
import org.iatoki.judgels.sandalphon.problem.base.AbstractProblemController;
import org.iatoki.judgels.sandalphon.problem.base.ProblemRoleChecker;
import org.iatoki.judgels.sandalphon.problem.base.ProblemStore;
import org.iatoki.judgels.sandalphon.problem.base.version.html.listVersionsView;
import org.iatoki.judgels.sandalphon.problem.base.version.html.viewVersionLocalChangesView;
import org.iatoki.judgels.sandalphon.resource.VersionCommitForm;
import play.data.Form;
import play.db.jpa.Transactional;
import play.filters.csrf.AddCSRFToken;
import play.filters.csrf.RequireCSRFCheck;
import play.mvc.Http;
import play.mvc.Result;

@Singleton
public final class ProblemVersionController extends AbstractProblemController {
    private final ProblemStore problemStore;
    private final ProblemRoleChecker problemRoleChecker;
    private final ProfileService profileService;

    @Inject
    public ProblemVersionController(
            ProblemStore problemStore,
            ProblemRoleChecker problemRoleChecker,
            ProfileService profileService) {

        super(problemStore, problemRoleChecker);
        this.problemStore = problemStore;
        this.problemRoleChecker = problemRoleChecker;
        this.profileService = profileService;
    }

    @Transactional(readOnly = true)
    public Result listVersionHistory(Http.Request req, long problemId) {
        String actorJid = getUserJid(req);
        Problem problem = checkFound(problemStore.findProblemById(problemId));
        checkAllowed(problemRoleChecker.isAllowedToViewVersionHistory(req, problem));

        List<GitCommit> versions = problemStore.getVersions(actorJid, problem.getJid());

        Set<String> userJids = versions.stream().map(GitCommit::getUserJid).collect(Collectors.toSet());
        Map<String, Profile> profilesMap = profileService.getProfiles(userJids);

        boolean isClean = !problemStore.userCloneExists(actorJid, problem.getJid());
        boolean isAllowedToRestoreVersionHistory = isClean && problemRoleChecker.isAllowedToRestoreVersionHistory(req, problem);

        HtmlTemplate template = getBaseHtmlTemplate(req);
        template.setContent(listVersionsView.render(versions, problem.getId(), profilesMap, isAllowedToRestoreVersionHistory));
        template.markBreadcrumbLocation("History", routes.ProblemVersionController.listVersionHistory(problem.getId()));
        template.setPageTitle("Problem - Versions - History");

        return renderTemplate(template, problem);
    }

    @Transactional(readOnly = true)
    public Result restoreVersionHistory(Http.Request req, long problemId, String hash) {
        String actorJid = getUserJid(req);
        Problem problem = checkFound(problemStore.findProblemById(problemId));

        boolean isClean = !problemStore.userCloneExists(actorJid, problem.getJid());
        checkAllowed(problemRoleChecker.isAllowedToRestoreVersionHistory(req, problem) && isClean);

        problemStore.restore(problem.getJid(), hash);

        return redirect(routes.ProblemVersionController.listVersionHistory(problem.getId()));
    }

    @Transactional(readOnly = true)
    @AddCSRFToken
    public Result viewVersionLocalChanges(Http.Request req, long problemId) {
        String actorJid = getUserJid(req);
        Problem problem = checkFound(problemStore.findProblemById(problemId));
        checkAllowed(problemRoleChecker.isPartnerOrAbove(req, problem));

        boolean isClean = !problemStore.userCloneExists(actorJid, problem.getJid());

        Form<VersionCommitForm> versionCommitForm = formFactory.form(VersionCommitForm.class);

        return showViewVersionLocalChanges(req, versionCommitForm, problem, isClean);
    }

    @Transactional
    @RequireCSRFCheck
    public Result postCommitVersionLocalChanges(Http.Request req, long problemId) {
        String actorJid = getUserJid(req);
        Problem problem = checkFound(problemStore.findProblemById(problemId));
        checkAllowed(problemRoleChecker.isPartnerOrAbove(req, problem));

        Form<VersionCommitForm> versionCommitForm = formFactory.form(VersionCommitForm.class).bindFromRequest();
        if (formHasErrors(versionCommitForm)) {
            boolean isClean = !problemStore.userCloneExists(actorJid, problem.getJid());
            return showViewVersionLocalChanges(req, versionCommitForm, problem, isClean);
        }

        VersionCommitForm versionCommitData = versionCommitForm.get();

        if (problemStore.fetchUserClone(actorJid, problem.getJid())) {
            flash("localChangesError", "Your working copy has diverged from the master copy. Please update your working copy.");
        } else if (!problemStore.commitThenMergeUserClone(actorJid, problem.getJid(), versionCommitData.title, versionCommitData.description)) {
            flash("localChangesError", "Your local changes conflict with the master copy. Please remember, discard, and then reapply your local changes.");
        } else if (!problemStore.pushUserClone(actorJid, problem.getJid())) {
            flash("localChangesError", "Your local changes conflict with the master copy. Please remember, discard, and then reapply your local changes.");
        } else {
            try {
                problemStore.discardUserClone(actorJid, problem.getJid());
            } catch (IOException e) {
                // do nothing
            }
        }

        return redirect(routes.ProblemVersionController.viewVersionLocalChanges(problem.getId()));
    }

    @Transactional(readOnly = true)
    public Result editVersionLocalChanges(Http.Request req, long problemId) {
        String actorJid = getUserJid(req);
        Problem problem = checkFound(problemStore.findProblemById(problemId));
        checkAllowed(problemRoleChecker.isPartnerOrAbove(req, problem));

        problemStore.fetchUserClone(actorJid, problem.getJid());

        if (!problemStore.updateUserClone(actorJid, problem.getJid())) {
            flash("localChangesError", "Your local changes conflict with the master copy. Please remember, discard, and then reapply your local changes.");
        }

        return redirect(routes.ProblemVersionController.viewVersionLocalChanges(problem.getId()));
    }

    @Transactional(readOnly = true)
    public Result discardVersionLocalChanges(Http.Request req, long problemId) {
        String actorJid = getUserJid(req);
        Problem problem = checkFound(problemStore.findProblemById(problemId));
        checkAllowed(problemRoleChecker.isPartnerOrAbove(req, problem));

        try {
            problemStore.discardUserClone(actorJid, problem.getJid());

            return redirect(routes.ProblemVersionController.viewVersionLocalChanges(problem.getId()));
        } catch (IOException e) {
            return notFound();
        }
    }

    private Result showViewVersionLocalChanges(Http.Request req, Form<VersionCommitForm> versionCommitForm, Problem problem, boolean isClean) {
        HtmlTemplate template = getBaseHtmlTemplate(req);
        template.setContent(viewVersionLocalChangesView.render(versionCommitForm, problem, isClean));
        template.markBreadcrumbLocation("Local changes", routes.ProblemVersionController.viewVersionLocalChanges(problem.getId()));
        template.setPageTitle("Problem - Versions - Local changes");

        return renderTemplate(template, problem);
    }

    protected Result renderTemplate(HtmlTemplate template, Problem problem) {
        template.addSecondaryTab("Local changes", routes.ProblemVersionController.viewVersionLocalChanges(problem.getId()));

        if (problemRoleChecker.isAllowedToViewVersionHistory(template.getRequest(), problem)) {
            template.addSecondaryTab("History", routes.ProblemVersionController.listVersionHistory(problem.getId()));
        }

        template.markBreadcrumbLocation("Versions", org.iatoki.judgels.sandalphon.problem.base.routes.ProblemController.jumpToVersions(problem.getId()));

        return super.renderTemplate(template, problem);
    }
}
