package org.iatoki.judgels.sandalphon.problem.programming.submission;

import static judgels.service.ServiceUtils.checkAllowed;
import static judgels.service.ServiceUtils.checkFound;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.core.MediaType;
import judgels.gabriel.api.GradingConfig;
import judgels.gabriel.api.LanguageRestriction;
import judgels.gabriel.api.SubmissionSource;
import judgels.gabriel.engines.GradingEngineRegistry;
import judgels.gabriel.languages.GradingLanguageRegistry;
import judgels.jophiel.api.profile.Profile;
import judgels.jophiel.api.profile.ProfileService;
import judgels.persistence.api.Page;
import judgels.sandalphon.api.problem.Problem;
import judgels.sandalphon.api.problem.programming.ProblemSubmissionConfig;
import judgels.sandalphon.api.submission.programming.Submission;
import judgels.sandalphon.api.submission.programming.SubmissionData;
import judgels.sandalphon.submission.programming.SubmissionClient;
import judgels.sandalphon.submission.programming.SubmissionSourceBuilder;
import judgels.sandalphon.submission.programming.SubmissionStore;
import org.glassfish.jersey.media.multipart.FormDataBodyPart;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataMultiPart;
import org.iatoki.judgels.play.forms.ListTableSelectionForm;
import org.iatoki.judgels.play.template.HtmlTemplate;
import org.iatoki.judgels.sandalphon.problem.base.AbstractProblemController;
import org.iatoki.judgels.sandalphon.problem.base.ProblemRoleChecker;
import org.iatoki.judgels.sandalphon.problem.base.ProblemStore;
import org.iatoki.judgels.sandalphon.problem.programming.ProgrammingProblemStore;
import org.iatoki.judgels.sandalphon.problem.programming.grading.GradingEngineAdapterRegistry;
import org.iatoki.judgels.sandalphon.problem.programming.submission.html.listSubmissionsView;
import play.db.jpa.Transactional;
import play.mvc.Http;
import play.mvc.Result;

@Singleton
public final class ProgrammingProblemSubmissionController extends AbstractProblemController {
    private final ProblemStore problemStore;
    private final ProblemRoleChecker problemRoleChecker;
    private final ProgrammingProblemStore programmingProblemStore;
    private final ProfileService profileService;
    private final SubmissionStore submissionStore;
    private final SubmissionSourceBuilder submissionSourceBuilder;
    private final SubmissionClient submissionClient;
    private final SubmissionRegrader submissionRegrader;

    @Inject
    public ProgrammingProblemSubmissionController(
            ProblemStore problemStore,
            ProblemRoleChecker problemRoleChecker,
            ProgrammingProblemStore programmingProblemStore,
            ProfileService profileService,
            SubmissionStore submissionStore,
            SubmissionSourceBuilder submissionSourceBuilder,
            SubmissionClient submissionClient,
            SubmissionRegrader submissionRegrader) {

        super(problemStore, problemRoleChecker);
        this.problemStore = problemStore;
        this.problemRoleChecker = problemRoleChecker;
        this.programmingProblemStore = programmingProblemStore;
        this.profileService = profileService;
        this.submissionStore = submissionStore;
        this.submissionSourceBuilder = submissionSourceBuilder;
        this.submissionClient = submissionClient;
        this.submissionRegrader = submissionRegrader;
    }

    @Transactional
    public Result postSubmit(Http.Request req, long problemId) {
        String actorJid = getUserJid(req);
        Problem problem = checkFound(problemStore.findProblemById(problemId));

        boolean isClean = !problemStore.userCloneExists(actorJid, problem.getJid());
        checkAllowed(problemRoleChecker.isAllowedToSubmit(req, problem) && isClean);

        String gradingEngine;
        try {
            gradingEngine = programmingProblemStore.getGradingEngine(null, problem.getJid());
        } catch (IOException e) {
            gradingEngine = GradingEngineRegistry.getInstance().getDefault();
        }

        GradingConfig gradingConfig;
        try {
            gradingConfig = programmingProblemStore.getGradingConfig(null, problem.getJid());
        } catch (IOException e) {
            gradingConfig = GradingEngineRegistry.getInstance()
                    .get(gradingEngine)
                    .createDefaultConfig();
        }

        Http.MultipartFormData<File> body = request().body().asMultipartFormData();

        String gradingLanguage = body.asFormUrlEncoded().get("language")[0];

        LanguageRestriction languageRestriction;
        try {
            languageRestriction = programmingProblemStore.getLanguageRestriction(null, problem.getJid());
        } catch (IOException e) {
            languageRestriction = LanguageRestriction.noRestriction();
        }

        FormDataMultiPart parts = new FormDataMultiPart();
        try {
            for (Http.MultipartFormData.FilePart<File> part : body.getFiles()) {
                parts.getBodyParts().add(new FormDataBodyPart(
                        FormDataContentDisposition.name(part.getKey()).fileName(part.getFilename()).build(),
                        Files.readAllBytes(part.getRef().toPath()),
                        MediaType.MULTIPART_FORM_DATA_TYPE));
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        SubmissionSource source = submissionSourceBuilder.fromNewSubmission(parts);
        SubmissionData data = new SubmissionData.Builder()
                .problemJid(problem.getJid())
                .containerJid(problem.getJid())
                .gradingLanguage(gradingLanguage)
                .build();
        ProblemSubmissionConfig config = new ProblemSubmissionConfig.Builder()
                .sourceKeys(gradingConfig.getSourceFileFields())
                .gradingEngine(gradingEngine)
                .gradingLanguageRestriction(languageRestriction)
                .build();
        Submission submission = submissionClient.submit(data, source, config);
        submissionSourceBuilder.storeSubmissionSource(submission.getJid(), source);

        return redirect(routes.ProgrammingProblemSubmissionController.viewSubmissions(problem.getId()));
    }

    @Transactional(readOnly = true)
    public Result viewSubmissions(Http.Request req, long problemId)  {
        return listSubmissions(req, problemId, 0, "id", "desc");
    }

    @Transactional(readOnly = true)
    public Result listSubmissions(Http.Request req, long problemId, long pageIndex, String orderBy, String orderDir) {
        Problem problem = checkFound(problemStore.findProblemById(problemId));
        checkAllowed(problemRoleChecker.isAllowedToSubmit(req, problem));

        Page<Submission> pageOfProgrammingSubmissions = submissionStore.getSubmissions(Optional.empty(), Optional.empty(), Optional.of(problem.getJid()), Optional.of((int) pageIndex + 1));
        Map<String, String> gradingLanguageToNameMap = GradingLanguageRegistry.getInstance().getNamesMap();

        Set<String> userJids = pageOfProgrammingSubmissions.getPage().stream().map(Submission::getUserJid).collect(Collectors.toSet());
        Map<String, Profile> profilesMap = profileService.getProfiles(userJids);

        HtmlTemplate template = getBaseHtmlTemplate(req);
        template.setContent(listSubmissionsView.render(pageOfProgrammingSubmissions, gradingLanguageToNameMap, problemId, profilesMap, pageIndex, orderBy, orderDir));
        template.markBreadcrumbLocation("Submissions", routes.ProgrammingProblemSubmissionController.viewSubmissions(problemId));
        template.setPageTitle("Problem - Submissions");

        return renderTemplate(template, problem);
    }

    @Transactional(readOnly = true)
    public Result viewSubmission(Http.Request req, long problemId, long submissionId) {
        String actorJid = getUserJid(req);
        Problem problem = checkFound(problemStore.findProblemById(problemId));
        checkAllowed(problemRoleChecker.isAllowedToSubmit(req, problem));

        Submission programmingSubmission = submissionStore.getSubmissionById(submissionId).get();

        String engine;
        try {
            engine = programmingProblemStore.getGradingEngine(actorJid, problem.getJid());
        } catch (IOException e) {
            engine = GradingEngineRegistry.getInstance().getDefault();
        }
        SubmissionSource submissionSource = submissionSourceBuilder.fromPastSubmission(programmingSubmission.getJid());

        Profile profile = profileService.getProfile(programmingSubmission.getUserJid());

        HtmlTemplate template = getBaseHtmlTemplate(req);
        template.setContent(GradingEngineAdapterRegistry.getInstance().getByGradingEngineName(engine).renderViewSubmission(programmingSubmission, submissionSource, profile, null, problem.getSlug(), GradingLanguageRegistry.getInstance().get(programmingSubmission.getGradingLanguage()).getName(), null));

        template.markBreadcrumbLocation("View submission", routes.ProgrammingProblemSubmissionController.viewSubmission(problemId, submissionId));
        template.setPageTitle("Problem - View submission");

        return renderTemplate(template, problem);
    }

    @Transactional
    public Result regradeSubmission(Http.Request req, long problemId, long submissionId, long pageIndex, String orderBy, String orderDir) {
        Problem problem = checkFound(problemStore.findProblemById(problemId));
        checkAllowed(problemRoleChecker.isAllowedToSubmit(req, problem));

        Submission programmingSubmission = submissionStore.getSubmissionById(submissionId).get();
        submissionRegrader.regradeSubmission(programmingSubmission);

        return redirect(routes.ProgrammingProblemSubmissionController.listSubmissions(problemId, pageIndex, orderBy, orderDir));
    }

    @Transactional
    public Result regradeSubmissions(Http.Request req, long problemId, long pageIndex, String orderBy, String orderDir) {
        Problem problem = checkFound(problemStore.findProblemById(problemId));
        checkAllowed(problemRoleChecker.isAllowedToSubmit(req, problem));

        ListTableSelectionForm data = formFactory.form(ListTableSelectionForm.class).bindFromRequest().get();

        List<Submission> programmingSubmissions;

        if (data.selectAll) {
            programmingSubmissions = submissionStore.getSubmissions(Optional.empty(), Optional.empty(), Optional.of(problem.getJid()), Optional.empty()).getPage();
        } else if (data.selectJids != null) {
            programmingSubmissions = submissionStore.getSubmissionByJids(data.selectJids);
        } else {
            return redirect(routes.ProgrammingProblemSubmissionController.listSubmissions(problemId, pageIndex, orderBy, orderDir));
        }
        submissionRegrader.regradeSubmissions(programmingSubmissions);

        return redirect(routes.ProgrammingProblemSubmissionController.listSubmissions(problemId, pageIndex, orderBy, orderDir));
    }

    protected Result renderTemplate(HtmlTemplate template, Problem problem) {
        template.markBreadcrumbLocation("Submissions", org.iatoki.judgels.sandalphon.problem.programming.routes.ProgrammingProblemController.jumpToSubmissions(problem.getId()));

        return super.renderTemplate(template, problem);
    }
}
