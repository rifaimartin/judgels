package org.iatoki.judgels.sandalphon.problem.bundle.submission;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.gson.Gson;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.persistence.metamodel.SingularAttribute;
import judgels.fs.FileSystem;
import judgels.persistence.api.Page;
import org.iatoki.judgels.sandalphon.problem.bundle.grading.AbstractBundleGradingModel;
import org.iatoki.judgels.sandalphon.problem.bundle.grading.AbstractBundleGradingModel_;
import org.iatoki.judgels.sandalphon.problem.bundle.grading.BaseBundleGradingDao;
import org.iatoki.judgels.sandalphon.problem.bundle.grading.BundleAnswer;
import org.iatoki.judgels.sandalphon.problem.bundle.grading.BundleGradingResult;
import org.iatoki.judgels.sandalphon.problem.bundle.grading.BundleProblemGrader;
import play.data.DynamicForm;

public abstract class AbstractBundleSubmissionStore<SM extends AbstractBundleSubmissionModel, GM extends AbstractBundleGradingModel> {

    private final BaseBundleSubmissionDao<SM> bundleSubmissionDao;
    private final BaseBundleGradingDao<GM> bundleGradingDao;
    private final BundleProblemGrader bundleProblemGrader;

    protected AbstractBundleSubmissionStore(BaseBundleSubmissionDao<SM> bundleSubmissionDao, BaseBundleGradingDao<GM> bundleGradingDao, BundleProblemGrader bundleProblemGrader) {
        this.bundleSubmissionDao = bundleSubmissionDao;
        this.bundleGradingDao = bundleGradingDao;
        this.bundleProblemGrader = bundleProblemGrader;
    }

    public Optional<BundleSubmission> findBundleSubmissionById(long submissionId) {
        return bundleSubmissionDao.select(submissionId).map(sm -> {
            List<GM> gradingModels = bundleGradingDao.findSortedByFiltersEq("id", "asc", "", ImmutableMap.of(AbstractBundleGradingModel_.submissionJid, sm.jid), 0, -1);
            return BundleSubmissionServiceUtils.createSubmissionFromModels(sm, gradingModels);
        });
    }

    public List<BundleSubmission> getAllBundleSubmissions() {
        List<SM> submissionModels = bundleSubmissionDao.getAll();
        Map<String, List<GM>> gradingModelsMap = bundleGradingDao.getBySubmissionJids(Lists.transform(submissionModels, m -> m.jid));

        return Lists.transform(submissionModels, m -> BundleSubmissionServiceUtils.createSubmissionFromModels(m, gradingModelsMap.get(m.jid)));
    }

    public List<BundleSubmission> getBundleSubmissionsByFilters(String orderBy, String orderDir, String authorJid, String problemJid, String containerJid) {
        ImmutableMap.Builder<SingularAttribute<? super SM, ?>, String> filterColumnsBuilder = ImmutableMap.builder();
        if (authorJid != null) {
            filterColumnsBuilder.put(AbstractBundleSubmissionModel_.createdBy, authorJid);
        }
        if (problemJid != null) {
            filterColumnsBuilder.put(AbstractBundleSubmissionModel_.problemJid, problemJid);
        }
        if (containerJid != null) {
            filterColumnsBuilder.put(AbstractBundleSubmissionModel_.containerJid, containerJid);
        }

        Map<SingularAttribute<? super SM, ?>, String> filterColumns = filterColumnsBuilder.build();

        List<SM> submissionModels = bundleSubmissionDao.findSortedByFiltersEq(orderBy, orderDir, "", filterColumns, 0, -1);

        return Lists.transform(submissionModels, m -> BundleSubmissionServiceUtils.createSubmissionFromModel(m));
    }

    public List<BundleSubmission> getBundleSubmissionsByJids(List<String> submissionJids) {
        List<SM> submissionModels = bundleSubmissionDao.getByJids(submissionJids);

        return Lists.transform(submissionModels, m -> BundleSubmissionServiceUtils.createSubmissionFromModel(m));
    }

    public Page<BundleSubmission> getPageOfBundleSubmissions(long pageIndex, long pageSize, String orderBy, String orderDir, String authorJid, String problemJid, String containerJid) {
        ImmutableMap.Builder<SingularAttribute<? super SM, ?>, String> filterColumnsBuilder = ImmutableMap.builder();
        if (authorJid != null) {
            filterColumnsBuilder.put(AbstractBundleSubmissionModel_.createdBy, authorJid);
        }
        if (problemJid != null) {
            filterColumnsBuilder.put(AbstractBundleSubmissionModel_.problemJid, problemJid);
        }
        if (containerJid != null) {
            filterColumnsBuilder.put(AbstractBundleSubmissionModel_.containerJid, containerJid);
        }

        Map<SingularAttribute<? super SM, ?>, String> filterColumns = filterColumnsBuilder.build();

        long totalRowsCount = bundleSubmissionDao.countByFiltersEq("", filterColumns);
        List<SM> submissionModels = bundleSubmissionDao.findSortedByFiltersEq(orderBy, orderDir, "", filterColumns, pageIndex * pageSize, pageSize);
        Map<String, List<GM>> gradingModelsMap = bundleGradingDao.getBySubmissionJids(Lists.transform(submissionModels, m -> m.jid));

        List<BundleSubmission> submissions = Lists.transform(submissionModels, m -> BundleSubmissionServiceUtils.createSubmissionFromModels(m, gradingModelsMap.get(m.jid)));

        return new Page.Builder<BundleSubmission>()
                .page(submissions)
                .totalCount(totalRowsCount)
                .pageIndex(pageIndex)
                .pageSize(pageSize)
                .build();
    }

    public final String submit(String problemJid, String containerJid, BundleAnswer answer) {
        SM submissionModel = bundleSubmissionDao.createSubmissionModel();

        submissionModel.problemJid = problemJid;
        submissionModel.containerJid = containerJid;

        bundleSubmissionDao.update(submissionModel);

        grade(submissionModel, answer);

        return submissionModel.jid;
    }

    public final void regrade(String submissionJid, BundleAnswer answer) {
        SM submissionModel = bundleSubmissionDao.findByJid(submissionJid);

        grade(submissionModel, answer);
    }

    public void afterGrade(String gradingJid, BundleAnswer answer) {
        // To be overridden if needed
    }

    public void storeSubmissionFiles(FileSystem localFs, FileSystem remoteFs, String submissionJid, BundleAnswer answer) {
        List<FileSystem> fileSystemProviders = Lists.newArrayList(localFs);
        if (remoteFs != null) {
            fileSystemProviders.add(remoteFs);
        }

        for (FileSystem fileSystemProvider : fileSystemProviders) {
            fileSystemProvider.createDirectory(Paths.get(submissionJid));

            fileSystemProvider.writeToFile(Paths.get(submissionJid, "answer.json"), new Gson().toJson(answer));
        }
    }

    public BundleAnswer createBundleAnswerFromNewSubmission(DynamicForm data, String languageCode) {
        return new BundleAnswer(data.rawData(), languageCode);
    }

    public BundleAnswer createBundleAnswerFromPastSubmission(FileSystem localFs, FileSystem remoteFs, String submissionJid) throws IOException {
        FileSystem fileSystemProvider;

        if (localFs.directoryExists(Paths.get(submissionJid))) {
            fileSystemProvider = localFs;
        } else {
            fileSystemProvider = remoteFs;
        }

        return new Gson().fromJson(fileSystemProvider.readFromFile(Paths.get(submissionJid, "answer.json")), BundleAnswer.class);
    }

    private void grade(SM submissionModel, BundleAnswer answer) {
        try {
            BundleGradingResult bundleGradingResult = bundleProblemGrader.gradeBundleProblem(submissionModel.problemJid, answer);

            if (bundleGradingResult != null) {
                GM gradingModel = bundleGradingDao.createGradingModel();

                gradingModel.submissionJid = submissionModel.jid;
                gradingModel.score = (int) bundleGradingResult.getScore();
                gradingModel.details = bundleGradingResult.getDetailsAsJson();

                bundleGradingDao.insert(gradingModel);

                afterGrade(gradingModel.jid, answer);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
