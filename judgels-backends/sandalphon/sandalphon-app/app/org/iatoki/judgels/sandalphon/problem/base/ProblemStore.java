package org.iatoki.judgels.sandalphon.problem.base;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.inject.Inject;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import judgels.fs.FileInfo;
import judgels.fs.FileSystem;
import judgels.persistence.JidGenerator;
import judgels.persistence.api.Page;
import judgels.sandalphon.api.problem.Problem;
import judgels.sandalphon.api.problem.ProblemStatement;
import judgels.sandalphon.api.problem.ProblemType;
import judgels.sandalphon.api.problem.partner.ProblemPartner;
import judgels.sandalphon.api.problem.partner.ProblemPartnerChildConfig;
import judgels.sandalphon.api.problem.partner.ProblemPartnerConfig;
import org.iatoki.judgels.GitCommit;
import org.iatoki.judgels.Git;
import org.iatoki.judgels.play.jid.JidService;
import org.iatoki.judgels.sandalphon.SandalphonProperties;
import org.iatoki.judgels.sandalphon.StatementLanguageStatus;
import org.iatoki.judgels.sandalphon.problem.base.partner.ProblemPartnerDao;
import org.iatoki.judgels.sandalphon.problem.base.partner.ProblemPartnerModel;
import org.iatoki.judgels.sandalphon.problem.base.partner.ProblemPartnerModel_;

public class ProblemStore {
    private final ObjectMapper mapper;
    private final ProblemDao problemDao;
    private final FileSystem problemFs;
    private final Git problemGit;
    private final ProblemPartnerDao problemPartnerDao;

    @Inject
    public ProblemStore(ObjectMapper mapper, ProblemDao problemDao, @ProblemFs FileSystem problemFs, @ProblemGit Git problemGit, ProblemPartnerDao problemPartnerDao) {
        this.mapper = mapper;
        this.problemDao = problemDao;
        this.problemFs = problemFs;
        this.problemGit = problemGit;
        this.problemPartnerDao = problemPartnerDao;
    }

    public Problem createProblem(ProblemType type, String slug, String additionalNote, String initialLanguageCode) {
        ProblemModel problemModel = new ProblemModel();
        problemModel.slug = slug;
        problemModel.additionalNote = additionalNote;

        problemDao.insertWithJid(JidGenerator.newChildJid(ProblemModel.class, type.ordinal()), problemModel);

        initStatements(problemModel.jid, initialLanguageCode);
        problemFs.createDirectory(getClonesDirPath(problemModel.jid));

        return createProblemFromModel(problemModel);
    }

    public boolean problemExistsByJid(String problemJid) {
        return problemDao.existsByJid(problemJid);
    }

    public boolean problemExistsBySlug(String slug) {
        return problemDao.existsBySlug(slug);
    }

    public Optional<Problem> findProblemById(long problemId) {
        return problemDao.select(problemId).map(m -> createProblemFromModel(m));
    }

    public Problem findProblemByJid(String problemJid) {
        ProblemModel problemModel = problemDao.findByJid(problemJid);

        return createProblemFromModel(problemModel);
    }

    public Problem findProblemBySlug(String slug) {
        ProblemModel problemModel = problemDao.findBySlug(slug);

        return createProblemFromModel(problemModel);
    }

    public boolean isUserPartnerForProblem(String problemJid, String userJid) {
        return problemPartnerDao.existsByProblemJidAndPartnerJid(problemJid, userJid);
    }

    public void createProblemPartner(String problemJid, String userJid, ProblemPartnerConfig baseConfig, ProblemPartnerChildConfig childConfig) {
        ProblemModel problemModel = problemDao.findByJid(problemJid);

        ProblemPartnerModel problemPartnerModel = new ProblemPartnerModel();
        problemPartnerModel.problemJid = problemModel.jid;
        problemPartnerModel.userJid = userJid;

        try {
            problemPartnerModel.baseConfig = mapper.writeValueAsString(baseConfig);
            problemPartnerModel.childConfig = mapper.writeValueAsString(childConfig);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        problemPartnerDao.insert(problemPartnerModel);

        problemDao.update(problemModel);
    }

    public void updateProblemPartner(long problemPartnerId, ProblemPartnerConfig baseConfig, ProblemPartnerChildConfig childConfig) {
        ProblemPartnerModel problemPartnerModel = problemPartnerDao.find(problemPartnerId);

        try {
            problemPartnerModel.baseConfig = mapper.writeValueAsString(baseConfig);
            problemPartnerModel.childConfig = mapper.writeValueAsString(childConfig);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        problemPartnerDao.update(problemPartnerModel);

        ProblemModel problemModel = problemDao.findByJid(problemPartnerModel.problemJid);

        problemDao.update(problemModel);
    }

    public Page<ProblemPartner> getPageOfProblemPartners(String problemJid, long pageIndex, long pageSize, String orderBy, String orderDir) {
        long totalRows = problemPartnerDao.countByFiltersEq("", ImmutableMap.of(ProblemPartnerModel_.problemJid, problemJid));
        List<ProblemPartnerModel> problemPartnerModels = problemPartnerDao.findSortedByFiltersEq(orderBy, orderDir, "", ImmutableMap.of(ProblemPartnerModel_.problemJid, problemJid), pageIndex * pageSize, pageSize);
        List<ProblemPartner> problemPartners = Lists.transform(problemPartnerModels, m -> createProblemPartnerFromModel(m));

        return new Page.Builder<ProblemPartner>()
                .page(problemPartners)
                .totalCount(totalRows)
                .pageIndex(pageIndex)
                .pageSize(pageSize)
                .build();
    }

    public Optional<ProblemPartner> findProblemPartnerById(long problemPartnerId) {
        return problemPartnerDao.select(problemPartnerId).map(m -> createProblemPartnerFromModel(m));
    }

    public ProblemPartner findProblemPartnerByProblemJidAndPartnerJid(String problemJid, String partnerJid) {
        ProblemPartnerModel problemPartnerModel = problemPartnerDao.findByProblemJidAndPartnerJid(problemJid, partnerJid);

        return createProblemPartnerFromModel(problemPartnerModel);
    }

    public void updateProblem(String problemJid, String slug, String additionalNote) {
        ProblemModel problemModel = problemDao.findByJid(problemJid);
        problemModel.slug = slug;
        problemModel.additionalNote = additionalNote;

        problemDao.update(problemModel);
    }

    public Page<Problem> getPageOfProblems(long pageIndex, long pageSize, String orderBy, String orderDir, String filterString, String userJid, boolean isAdmin) {
        if (isAdmin) {
            long totalRows = problemDao.countByFilters(filterString);
            List<ProblemModel> problemModels = problemDao.findSortedByFilters(orderBy, orderDir, filterString, pageIndex * pageSize, pageSize);

            List<Problem> problems = Lists.transform(problemModels, m -> createProblemFromModel(m));
            return new Page.Builder<Problem>()
                    .page(problems)
                    .totalCount(totalRows)
                    .pageIndex(pageIndex)
                    .pageSize(pageSize)
                    .build();
        } else {
            List<String> problemJidsWhereIsAuthor = problemDao.getJidsByAuthorJid(userJid);
            List<String> problemJidsWhereIsPartner = problemPartnerDao.getProblemJidsByPartnerJid(userJid);

            ImmutableSet.Builder<String> allowedProblemJidsBuilder = ImmutableSet.builder();
            allowedProblemJidsBuilder.addAll(problemJidsWhereIsAuthor);
            allowedProblemJidsBuilder.addAll(problemJidsWhereIsPartner);

            Set<String> allowedProblemJids = allowedProblemJidsBuilder.build();

            long totalRows = problemDao.countByFiltersIn(filterString, ImmutableMap.of(ProblemModel_.jid, allowedProblemJids));
            List<ProblemModel> problemModels = problemDao.findSortedByFiltersIn(orderBy, orderDir, filterString, ImmutableMap.of(ProblemModel_.jid, allowedProblemJids), pageIndex * pageSize, pageSize);

            List<Problem> problems = Lists.transform(problemModels, m -> createProblemFromModel(m));
            return new Page.Builder<Problem>()
                    .page(problems)
                    .totalCount(totalRows)
                    .pageIndex(pageIndex)
                    .pageSize(pageSize)
                    .build();
        }

    }

    public Map<String, StatementLanguageStatus> getAvailableLanguages(String userJid, String problemJid) {
        String langs = problemFs.readFromFile(getStatementAvailableLanguagesFilePath(userJid, problemJid));

        return new Gson().fromJson(langs, new TypeToken<Map<String, StatementLanguageStatus>>() {
        }.getType());
    }

    public void addLanguage(String userJid, String problemJid, String languageCode) throws IOException {
        String langs = problemFs.readFromFile(getStatementAvailableLanguagesFilePath(userJid, problemJid));
        Map<String, StatementLanguageStatus> availableLanguages = new Gson().fromJson(langs, new TypeToken<Map<String, StatementLanguageStatus>>() { }.getType());

        availableLanguages.put(languageCode, StatementLanguageStatus.ENABLED);

        ProblemStatement defaultLanguageStatement = getStatement(userJid, problemJid, getDefaultLanguage(userJid, problemJid));
        problemFs.writeToFile(getStatementTitleFilePath(userJid, problemJid, languageCode), defaultLanguageStatement.getTitle());
        problemFs.writeToFile(getStatementTextFilePath(userJid, problemJid, languageCode), defaultLanguageStatement.getText());
        problemFs.writeToFile(getStatementAvailableLanguagesFilePath(userJid, problemJid), new Gson().toJson(availableLanguages));
    }

    public void enableLanguage(String userJid, String problemJid, String languageCode) throws IOException {
        String langs = problemFs.readFromFile(getStatementAvailableLanguagesFilePath(userJid, problemJid));
        Map<String, StatementLanguageStatus> availableLanguages = new Gson().fromJson(langs, new TypeToken<Map<String, StatementLanguageStatus>>() { }.getType());

        availableLanguages.put(languageCode, StatementLanguageStatus.ENABLED);

        problemFs.writeToFile(getStatementAvailableLanguagesFilePath(userJid, problemJid), new Gson().toJson(availableLanguages));
    }

    public void disableLanguage(String userJid, String problemJid, String languageCode) throws IOException {
        String langs = problemFs.readFromFile(getStatementAvailableLanguagesFilePath(userJid, problemJid));
        Map<String, StatementLanguageStatus> availableLanguages = new Gson().fromJson(langs, new TypeToken<Map<String, StatementLanguageStatus>>() { }.getType());

        availableLanguages.put(languageCode, StatementLanguageStatus.DISABLED);

        problemFs.writeToFile(getStatementAvailableLanguagesFilePath(userJid, problemJid), new Gson().toJson(availableLanguages));
    }

    public void makeDefaultLanguage(String userJid, String problemJid, String languageCode) throws IOException {
        problemFs.writeToFile(getStatementDefaultLanguageFilePath(userJid, problemJid), languageCode);
    }

    public String getDefaultLanguage(String userJid, String problemJid) {
        return problemFs.readFromFile(getStatementDefaultLanguageFilePath(userJid, problemJid));
    }

    public ProblemStatement getStatement(String userJid, String problemJid, String languageCode) throws IOException {
        String title = problemFs.readFromFile(getStatementTitleFilePath(userJid, problemJid, languageCode));
        String text = problemFs.readFromFile(getStatementTextFilePath(userJid, problemJid, languageCode));

        return new ProblemStatement.Builder().title(title).text(text).build();
    }

    public Map<String, String> getTitlesByLanguage(String userJid, String problemJid) {
        Map<String, StatementLanguageStatus> availableLanguages = getAvailableLanguages(userJid, problemJid);

        ImmutableMap.Builder<String, String> titlesByLanguageBuilder = ImmutableMap.builder();

        for (Map.Entry<String, StatementLanguageStatus> entry : availableLanguages.entrySet()) {
            if (entry.getValue() == StatementLanguageStatus.ENABLED) {
                String title = problemFs.readFromFile(getStatementTitleFilePath(userJid, problemJid, entry.getKey()));
                titlesByLanguageBuilder.put(entry.getKey(), title);
            }
        }

        return titlesByLanguageBuilder.build();
    }

    public void updateStatement(String userJid, String problemJid, String languageCode, ProblemStatement statement) throws IOException {
        ProblemModel problemModel = problemDao.findByJid(problemJid);
        problemFs.writeToFile(getStatementTitleFilePath(userJid, problemModel.jid, languageCode), statement.getTitle());
        problemFs.writeToFile(getStatementTextFilePath(userJid, problemModel.jid, languageCode), statement.getText());
    }

    public void uploadStatementMediaFile(String userJid, String problemJid, File mediaFile, String filename) throws IOException {
        ProblemModel problemModel = problemDao.findByJid(problemJid);
        Path mediaDirPath = getStatementMediaDirPath(userJid, problemModel.jid);
        problemFs.uploadPublicFile(mediaDirPath.resolve(filename), new FileInputStream(mediaFile));
    }

    public void uploadStatementMediaFileZipped(String userJid, String problemJid, File mediaFileZipped) throws IOException {
        ProblemModel problemModel = problemDao.findByJid(problemJid);
        Path mediaDirPath = getStatementMediaDirPath(userJid, problemModel.jid);
        problemFs.uploadZippedFiles(mediaDirPath, mediaFileZipped, false);
    }

    public List<FileInfo> getStatementMediaFiles(String userJid, String problemJid) {
        Path mediaDirPath = getStatementMediaDirPath(userJid, problemJid);
        return problemFs.listFilesInDirectory(mediaDirPath);
    }

    public String getStatementMediaFileURL(String userJid, String problemJid, String filename) {
        Path mediaFilePath = getStatementMediaDirPath(userJid, problemJid).resolve(filename);
        return problemFs.getPublicFileUrl(mediaFilePath);
    }

    public List<GitCommit> getVersions(String userJid, String problemJid) {
        Path root = getRootDirPath(problemFs, userJid, problemJid);
        return problemGit.getLog(root);
    }

    public void initRepository(String userJid, String problemJid) {
        Path root = getRootDirPath(problemFs, null, problemJid);

        problemGit.init(root);
        problemGit.addAll(root);
        problemGit.commit(root, userJid, "no@email.com", "Initial commit", "");
    }

    public boolean userCloneExists(String userJid, String problemJid) {
        Path root = getCloneDirPath(userJid, problemJid);

        return problemFs.directoryExists(root);
    }

    public void createUserCloneIfNotExists(String userJid, String problemJid) {
        Path origin = getOriginDirPath(problemJid);
        Path root = getCloneDirPath(userJid, problemJid);

        if (!problemFs.directoryExists(root)) {
            problemGit.clone(origin, root);
        }
    }

    public boolean commitThenMergeUserClone(String userJid, String problemJid, String title, String text) {
        Path root = getCloneDirPath(userJid, problemJid);

        problemGit.addAll(root);
        problemGit.commit(root, userJid, "no@email.com", title, text);
        boolean success = problemGit.rebase(root);

        if (!success) {
            problemGit.resetToParent(root);
        } else {
            ProblemModel problemModel = problemDao.findByJid(problemJid);

            problemDao.update(problemModel);
        }

        return success;
    }

    public boolean updateUserClone(String userJid, String problemJid) {
        Path root = getCloneDirPath(userJid, problemJid);

        problemGit.addAll(root);
        problemGit.commit(root, userJid, "no@email.com", "dummy", "dummy");
        boolean success = problemGit.rebase(root);

        problemGit.resetToParent(root);

        return success;
    }

    public boolean pushUserClone(String userJid, String problemJid) {
        Path origin = getOriginDirPath(problemJid);
        Path root = getRootDirPath(problemFs, userJid, problemJid);

        if (problemGit.push(root)) {
            problemGit.resetHard(origin);

            ProblemModel problemModel = problemDao.findByJid(problemJid);

            problemDao.update(problemModel);

            return true;
        }
        return false;
    }

    public boolean fetchUserClone(String userJid, String problemJid) {
        Path root = getRootDirPath(problemFs, userJid, problemJid);

        return problemGit.fetch(root);
    }

    public void discardUserClone(String userJid, String problemJid) throws IOException {
        Path root = getRootDirPath(problemFs, userJid, problemJid);

        problemFs.removeFile(root);
    }

    public void restore(String problemJid, String hash) {
        Path root = getOriginDirPath(problemJid);

        problemGit.restore(root, hash);

        ProblemModel problemModel = problemDao.findByJid(problemJid);

        problemDao.update(problemModel);
    }

    private void initStatements(String problemJid, String initialLanguageCode) {
        Path statementsDirPath = getStatementsDirPath(null, problemJid);
        problemFs.createDirectory(statementsDirPath);

        Path statementDirPath = getStatementDirPath(null, problemJid, initialLanguageCode);
        problemFs.createDirectory(statementDirPath);

        Path mediaDirPath = getStatementMediaDirPath(null, problemJid);
        problemFs.createDirectory(mediaDirPath);
        problemFs.createFile(mediaDirPath.resolve(".gitkeep"));

        problemFs.createFile(getStatementTitleFilePath(null, problemJid, initialLanguageCode));
        problemFs.createFile(getStatementTextFilePath(null, problemJid, initialLanguageCode));
        problemFs.writeToFile(getStatementDefaultLanguageFilePath(null, problemJid), initialLanguageCode);

        Map<String, StatementLanguageStatus> initialLanguage = ImmutableMap.of(initialLanguageCode, StatementLanguageStatus.ENABLED);
        problemFs.writeToFile(getStatementAvailableLanguagesFilePath(null, problemJid), new Gson().toJson(initialLanguage));
    }

    private Path getStatementsDirPath(String userJid, String problemJid) {
        return getRootDirPath(problemFs, userJid, problemJid).resolve("statements");
    }

    private Path getStatementDirPath(String userJid, String problemJid, String languageCode) {
        return getStatementsDirPath(userJid, problemJid).resolve(languageCode);
    }

    private Path getStatementTitleFilePath(String userJid, String problemJid, String languageCode) {
        return getStatementDirPath(userJid, problemJid, languageCode).resolve("title.txt");
    }

    private Path getStatementTextFilePath(String userJid, String problemJid, String languageCode) {
        return getStatementDirPath(userJid, problemJid, languageCode).resolve("text.html");
    }

    private Path getStatementDefaultLanguageFilePath(String userJid, String problemJid) {
        return getStatementsDirPath(userJid, problemJid).resolve("defaultLanguage.txt");
    }

    private Path getStatementAvailableLanguagesFilePath(String userJid, String problemJid) {
        return getStatementsDirPath(userJid, problemJid).resolve("availableLanguages.txt");
    }

    private Path getStatementMediaDirPath(String userJid, String problemJid) {
        return getStatementsDirPath(userJid, problemJid).resolve("resources");
    }

    private static ProblemType getProblemType(ProblemModel problemModel) {
        String prefix = JidService.getInstance().parsePrefix(problemModel.jid);

        if (prefix.equals("PROG")) {
            return ProblemType.PROGRAMMING;
        } else if (prefix.equals("BUND")) {
            return ProblemType.BUNDLE;
        } else {
            throw new IllegalStateException("Unknown problem type: " + prefix);
        }
    }

    private static Problem createProblemFromModel(ProblemModel problemModel) {
        return new Problem.Builder()
                .id(problemModel.id)
                .jid(problemModel.jid)
                .slug(problemModel.slug)
                .additionalNote(problemModel.additionalNote)
                .authorJid(problemModel.createdBy)
                .lastUpdateTime(problemModel.updatedAt)
                .type(getProblemType(problemModel))
                .build();
    }

    private ProblemPartner createProblemPartnerFromModel(ProblemPartnerModel problemPartnerModel) {
        try {
            return new ProblemPartner.Builder()
                    .id(problemPartnerModel.id)
                    .problemJid(problemPartnerModel.problemJid)
                    .userJid(problemPartnerModel.userJid)
                    .baseConfig(mapper.readValue(problemPartnerModel.baseConfig, ProblemPartnerConfig.class))
                    .childConfig(mapper.readValue(problemPartnerModel.childConfig, ProblemPartnerChildConfig.class))
                    .build();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static Path getOriginDirPath(String problemJid) {
        return Paths.get(SandalphonProperties.getInstance().getBaseProblemsDirKey(), problemJid);
    }

    private static Path getClonesDirPath(String problemJid) {
        return Paths.get(SandalphonProperties.getInstance().getBaseProblemClonesDirKey(), problemJid);
    }

    private static Path getCloneDirPath(String userJid, String problemJid) {
        return getClonesDirPath(problemJid).resolve(userJid);
    }

    private static Path getRootDirPath(FileSystem fs, String userJid, String problemJid) {
        Path origin = getOriginDirPath(problemJid);
        if (userJid == null) {
            return origin;
        }

        Path root = getCloneDirPath(userJid, problemJid);
        if (!fs.directoryExists(root)) {
            return origin;
        } else {
            return root;
        }
    }
}
