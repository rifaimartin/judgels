package org.iatoki.judgels.sandalphon.lesson;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.inject.Inject;
import judgels.fs.FileInfo;
import judgels.fs.FileSystem;
import judgels.persistence.api.Page;
import judgels.sandalphon.api.lesson.Lesson;
import judgels.sandalphon.api.lesson.LessonStatement;
import judgels.sandalphon.api.lesson.partner.LessonPartner;
import judgels.sandalphon.api.lesson.partner.LessonPartnerConfig;
import org.iatoki.judgels.GitCommit;
import org.iatoki.judgels.Git;
import org.iatoki.judgels.sandalphon.SandalphonProperties;
import org.iatoki.judgels.sandalphon.StatementLanguageStatus;
import org.iatoki.judgels.sandalphon.lesson.partner.LessonPartnerDao;
import org.iatoki.judgels.sandalphon.lesson.partner.LessonPartnerModel;
import org.iatoki.judgels.sandalphon.lesson.partner.LessonPartnerModel_;

public final class LessonStore {
    private final ObjectMapper mapper;
    private final LessonDao lessonDao;
    private final FileSystem lessonFs;
    private final Git lessonGit;
    private final LessonPartnerDao lessonPartnerDao;

    @Inject
    public LessonStore(ObjectMapper mapper, LessonDao lessonDao, @LessonFs FileSystem lessonFs, @LessonGit Git lessonGit, LessonPartnerDao lessonPartnerDao) {
        this.mapper = mapper;
        this.lessonDao = lessonDao;
        this.lessonFs = lessonFs;
        this.lessonGit = lessonGit;
        this.lessonPartnerDao = lessonPartnerDao;
    }

    public Lesson createLesson(String slug, String additionalNote, String initialLanguageCode) {
        LessonModel lessonModel = new LessonModel();
        lessonModel.slug = slug;
        lessonModel.additionalNote = additionalNote;

        lessonDao.insert(lessonModel);

        initStatements(lessonModel.jid, initialLanguageCode);
        lessonFs.createDirectory(getClonesDirPath(lessonModel.jid));

        return createLessonFromModel(lessonModel);
    }

    public boolean lessonExistsByJid(String lessonJid) {
        return lessonDao.existsByJid(lessonJid);
    }

    public boolean lessonExistsBySlug(String slug) {
        return lessonDao.existsBySlug(slug);
    }

    public Optional<Lesson> findLessonById(long lessonId) {
        return lessonDao.select(lessonId).map(m -> createLessonFromModel(m));
    }

    public Lesson findLessonByJid(String lessonJid) {
        LessonModel lessonModel = lessonDao.findByJid(lessonJid);

        return createLessonFromModel(lessonModel);
    }

    public Lesson findLessonBySlug(String slug) {
        LessonModel lessonModel = lessonDao.findBySlug(slug);

        return createLessonFromModel(lessonModel);
    }

    public boolean isUserPartnerForLesson(String lessonJid, String userJid) {
        return lessonPartnerDao.existsByLessonJidAndPartnerJid(lessonJid, userJid);
    }

    public void createLessonPartner(String lessonJid, String userJid, LessonPartnerConfig config) {
        LessonModel lessonModel = lessonDao.findByJid(lessonJid);

        LessonPartnerModel lessonPartnerModel = new LessonPartnerModel();
        lessonPartnerModel.lessonJid = lessonModel.jid;
        lessonPartnerModel.userJid = userJid;
        lessonPartnerModel.config = new Gson().toJson(config);

        lessonPartnerDao.insert(lessonPartnerModel);

        lessonDao.update(lessonModel);
    }

    public void updateLessonPartner(long lessonPartnerId, LessonPartnerConfig config) {
        LessonPartnerModel lessonPartnerModel = lessonPartnerDao.find(lessonPartnerId);
        lessonPartnerModel.config = new Gson().toJson(config);

        lessonPartnerDao.update(lessonPartnerModel);

        LessonModel lessonModel = lessonDao.findByJid(lessonPartnerModel.lessonJid);

        lessonDao.update(lessonModel);
    }

    public Page<LessonPartner> getPageOfLessonPartners(String lessonJid, long pageIndex, long pageSize, String orderBy, String orderDir) {
        long totalRows = lessonPartnerDao.countByFiltersEq("", ImmutableMap.of(LessonPartnerModel_.lessonJid, lessonJid));
        List<LessonPartnerModel> lessonPartnerModels = lessonPartnerDao.findSortedByFiltersEq(orderBy, orderDir, "", ImmutableMap.of(LessonPartnerModel_.lessonJid, lessonJid), pageIndex * pageSize, pageSize);
        List<LessonPartner> lessonPartners = Lists.transform(lessonPartnerModels, m -> createLessonPartnerFromModel(m));

        return new Page.Builder<LessonPartner>()
                .page(lessonPartners)
                .totalCount(totalRows)
                .pageIndex(pageIndex)
                .pageSize(pageSize)
                .build();
    }

    public Optional<LessonPartner> findLessonPartnerById(long lessonPartnerId) {
        return lessonPartnerDao.select(lessonPartnerId).map(m -> createLessonPartnerFromModel(m));
    }

    public LessonPartner findLessonPartnerByLessonJidAndPartnerJid(String lessonJid, String partnerJid) {
        LessonPartnerModel lessonPartnerModel = lessonPartnerDao.findByLessonJidAndPartnerJid(lessonJid, partnerJid);

        return createLessonPartnerFromModel(lessonPartnerModel);
    }

    public void updateLesson(String lessonJid, String slug, String additionalNote) {
        LessonModel lessonModel = lessonDao.findByJid(lessonJid);
        lessonModel.slug = slug;
        lessonModel.additionalNote = additionalNote;

        lessonDao.update(lessonModel);
    }

    public Page<Lesson> getPageOfLessons(long pageIndex, long pageSize, String orderBy, String orderDir, String filterString, String userJid, boolean isAdmin) {
        if (isAdmin) {
            long totalRows = lessonDao.countByFilters(filterString);
            List<LessonModel> lessonModels = lessonDao.findSortedByFilters(orderBy, orderDir, filterString, pageIndex * pageSize, pageSize);

            List<Lesson> lessons = Lists.transform(lessonModels, m -> createLessonFromModel(m));
            return new Page.Builder<Lesson>()
                    .page(lessons)
                    .totalCount(totalRows)
                    .pageIndex(pageIndex)
                    .pageSize(pageSize)
                    .build();
        } else {
            List<String> lessonJidsWhereIsAuthor = lessonDao.getJidsByAuthorJid(userJid);
            List<String> lessonJidsWhereIsPartner = lessonPartnerDao.getLessonJidsByPartnerJid(userJid);

            ImmutableSet.Builder<String> allowedLessonJidsBuilder = ImmutableSet.builder();
            allowedLessonJidsBuilder.addAll(lessonJidsWhereIsAuthor);
            allowedLessonJidsBuilder.addAll(lessonJidsWhereIsPartner);

            Set<String> allowedLessonJids = allowedLessonJidsBuilder.build();

            long totalRows = lessonDao.countByFiltersIn(filterString, ImmutableMap.of(LessonModel_.jid, allowedLessonJids));
            List<LessonModel> lessonModels = lessonDao.findSortedByFiltersIn(orderBy, orderDir, filterString, ImmutableMap.of(LessonModel_.jid, allowedLessonJids), pageIndex * pageSize, pageSize);

            List<Lesson> lessons = Lists.transform(lessonModels, m -> createLessonFromModel(m));
            return new Page.Builder<Lesson>()
                    .page(lessons)
                    .totalCount(totalRows)
                    .pageIndex(pageIndex)
                    .pageSize(pageSize)
                    .build();
        }

    }

    public Map<String, StatementLanguageStatus> getAvailableLanguages(String userJid, String lessonJid) {
        String langs = lessonFs.readFromFile(getStatementAvailableLanguagesFilePath(userJid, lessonJid));
        return new Gson().fromJson(langs, new TypeToken<Map<String, StatementLanguageStatus>>() { }.getType());
    }

    public void addLanguage(String userJid, String lessonJid, String languageCode) throws IOException {
        String langs = lessonFs.readFromFile(getStatementAvailableLanguagesFilePath(userJid, lessonJid));
        Map<String, StatementLanguageStatus> availableLanguages = new Gson().fromJson(langs, new TypeToken<Map<String, StatementLanguageStatus>>() { }.getType());

        availableLanguages.put(languageCode, StatementLanguageStatus.ENABLED);

        LessonStatement defaultLanguageStatement = getStatement(userJid, lessonJid, getDefaultLanguage(userJid, lessonJid));
        lessonFs.writeToFile(getStatementTitleFilePath(userJid, lessonJid, languageCode), defaultLanguageStatement.getTitle());
        lessonFs.writeToFile(getStatementTextFilePath(userJid, lessonJid, languageCode), defaultLanguageStatement.getText());
        lessonFs.writeToFile(getStatementAvailableLanguagesFilePath(userJid, lessonJid), new Gson().toJson(availableLanguages));
    }

    public void enableLanguage(String userJid, String lessonJid, String languageCode) throws IOException {
        String langs = lessonFs.readFromFile(getStatementAvailableLanguagesFilePath(userJid, lessonJid));
        Map<String, StatementLanguageStatus> availableLanguages = new Gson().fromJson(langs, new TypeToken<Map<String, StatementLanguageStatus>>() { }.getType());

        availableLanguages.put(languageCode, StatementLanguageStatus.ENABLED);

        lessonFs.writeToFile(getStatementAvailableLanguagesFilePath(userJid, lessonJid), new Gson().toJson(availableLanguages));
    }

    public void disableLanguage(String userJid, String lessonJid, String languageCode) throws IOException {
        String langs = lessonFs.readFromFile(getStatementAvailableLanguagesFilePath(userJid, lessonJid));
        Map<String, StatementLanguageStatus> availableLanguages = new Gson().fromJson(langs, new TypeToken<Map<String, StatementLanguageStatus>>() { }.getType());

        availableLanguages.put(languageCode, StatementLanguageStatus.DISABLED);

        lessonFs.writeToFile(getStatementAvailableLanguagesFilePath(userJid, lessonJid), new Gson().toJson(availableLanguages));
    }

    public void makeDefaultLanguage(String userJid, String lessonJid, String languageCode) throws IOException {
        lessonFs.writeToFile(getStatementDefaultLanguageFilePath(userJid, lessonJid), languageCode);
    }

    public String getDefaultLanguage(String userJid, String lessonJid) {
        return lessonFs.readFromFile(getStatementDefaultLanguageFilePath(userJid, lessonJid));
    }

    public LessonStatement getStatement(String userJid, String lessonJid, String languageCode) throws IOException {
        String title = lessonFs.readFromFile(getStatementTitleFilePath(userJid, lessonJid, languageCode));
        String text = lessonFs.readFromFile(getStatementTextFilePath(userJid, lessonJid, languageCode));

        return new LessonStatement.Builder().title(title).text(text).build();
    }

    public Map<String, String> getTitlesByLanguage(String userJid, String lessonJid) {
        Map<String, StatementLanguageStatus> availableLanguages = getAvailableLanguages(userJid, lessonJid);

        ImmutableMap.Builder<String, String> titlesByLanguageBuilder = ImmutableMap.builder();

        for (Map.Entry<String, StatementLanguageStatus> entry : availableLanguages.entrySet()) {
            if (entry.getValue() == StatementLanguageStatus.ENABLED) {
                String title = lessonFs.readFromFile(getStatementTitleFilePath(userJid, lessonJid, entry.getKey()));
                titlesByLanguageBuilder.put(entry.getKey(), title);
            }
        }

        return titlesByLanguageBuilder.build();
    }

    public void updateStatement(String userJid, String lessonJid, String languageCode, LessonStatement statement) throws IOException {
        LessonModel lessonModel = lessonDao.findByJid(lessonJid);
        lessonFs.writeToFile(getStatementTitleFilePath(userJid, lessonModel.jid, languageCode), statement.getTitle());
        lessonFs.writeToFile(getStatementTextFilePath(userJid, lessonModel.jid, languageCode), statement.getText());
    }

    public void uploadStatementMediaFile(String userJid, String lessonJid, File mediaFile, String filename) throws IOException {
        LessonModel lessonModel = lessonDao.findByJid(lessonJid);
        Path mediaDirPath = getStatementMediaDirPath(userJid, lessonModel.jid);
        lessonFs.uploadPublicFile(mediaDirPath.resolve(filename), new FileInputStream(mediaFile));
    }

    public void uploadStatementMediaFileZipped(String userJid, String lessonJid, File mediaFileZipped) throws IOException {
        LessonModel lessonModel = lessonDao.findByJid(lessonJid);
        Path mediaDirPath = getStatementMediaDirPath(userJid, lessonModel.jid);
        lessonFs.uploadZippedFiles(mediaDirPath, mediaFileZipped, false);
    }

    public List<FileInfo> getStatementMediaFiles(String userJid, String lessonJid) {
        Path mediaDirPath = getStatementMediaDirPath(userJid, lessonJid);
        return lessonFs.listFilesInDirectory(mediaDirPath);
    }

    public String getStatementMediaFileURL(String userJid, String lessonJid, String filename) {
        Path mediaFilePath = getStatementMediaDirPath(userJid, lessonJid).resolve(filename);
        return lessonFs.getPublicFileUrl(mediaFilePath);
    }

    public List<GitCommit> getVersions(String userJid, String lessonJid) {
        Path root = getRootDirPath(lessonFs, userJid, lessonJid);
        return lessonGit.getLog(root);
    }

    public void initRepository(String userJid, String lessonJid) {
        Path root = getRootDirPath(lessonFs, null, lessonJid);

        lessonGit.init(root);
        lessonGit.addAll(root);
        lessonGit.commit(root, userJid, "no@email.com", "Initial commit", "");
    }

    public boolean userCloneExists(String userJid, String lessonJid) {
        Path root = getCloneDirPath(userJid, lessonJid);

        return lessonFs.directoryExists(root);
    }

    public void createUserCloneIfNotExists(String userJid, String lessonJid) {
        Path origin = getOriginDirPath(lessonJid);
        Path root = getCloneDirPath(userJid, lessonJid);

        if (!lessonFs.directoryExists(root)) {
            lessonGit.clone(origin, root);
        }
    }

    public boolean commitThenMergeUserClone(String userJid, String lessonJid, String title, String description) {
        Path root = getCloneDirPath(userJid, lessonJid);

        lessonGit.addAll(root);
        lessonGit.commit(root, userJid, "no@email.com", title, description);
        boolean success = lessonGit.rebase(root);

        if (!success) {
            lessonGit.resetToParent(root);
        } else {
            LessonModel lessonModel = lessonDao.findByJid(lessonJid);
            lessonDao.update(lessonModel);
        }

        return success;
    }

    public boolean updateUserClone(String userJid, String lessonJid) {
        Path root = getCloneDirPath(userJid, lessonJid);

        lessonGit.addAll(root);
        lessonGit.commit(root, userJid, "no@email.com", "dummy", "dummy");
        boolean success = lessonGit.rebase(root);

        lessonGit.resetToParent(root);

        return success;
    }

    public boolean pushUserClone(String userJid, String lessonJid) {
        Path origin = getOriginDirPath(lessonJid);
        Path root = getRootDirPath(lessonFs, userJid, lessonJid);

        if (lessonGit.push(root)) {
            lessonGit.resetHard(origin);

            LessonModel lessonModel = lessonDao.findByJid(lessonJid);
            lessonDao.update(lessonModel);

            return true;
        }
        return false;
    }

    public boolean fetchUserClone(String userJid, String lessonJid) {
        Path root = getRootDirPath(lessonFs, userJid, lessonJid);

        return lessonGit.fetch(root);
    }

    public void discardUserClone(String userJid, String lessonJid) throws IOException {
        Path root = getRootDirPath(lessonFs, userJid, lessonJid);

        lessonFs.removeFile(root);
    }

    public void restore(String lessonJid, String hash) {
        Path root = getOriginDirPath(lessonJid);

        lessonGit.restore(root, hash);

        LessonModel lessonModel = lessonDao.findByJid(lessonJid);
        lessonDao.update(lessonModel);
    }

    private void initStatements(String lessonJid, String initialLanguageCode) {
        Path statementsDirPath = getStatementsDirPath(null, lessonJid);
        lessonFs.createDirectory(statementsDirPath);

        Path statementDirPath = getStatementDirPath(null, lessonJid, initialLanguageCode);
        lessonFs.createDirectory(statementDirPath);

        Path mediaDirPath = getStatementMediaDirPath(null, lessonJid);
        lessonFs.createDirectory(mediaDirPath);
        lessonFs.createFile(mediaDirPath.resolve(".gitkeep"));

        lessonFs.createFile(getStatementTitleFilePath(null, lessonJid, initialLanguageCode));
        lessonFs.createFile(getStatementTextFilePath(null, lessonJid, initialLanguageCode));
        lessonFs.writeToFile(getStatementDefaultLanguageFilePath(null, lessonJid), initialLanguageCode);

        Map<String, StatementLanguageStatus> initialLanguage = ImmutableMap.of(initialLanguageCode, StatementLanguageStatus.ENABLED);
        lessonFs.writeToFile(getStatementAvailableLanguagesFilePath(null, lessonJid), new Gson().toJson(initialLanguage));
    }

    private Path getStatementsDirPath(String userJid, String lessonJid) {
        return getRootDirPath(lessonFs, userJid, lessonJid).resolve("statements");
    }

    private Path getStatementDirPath(String userJid, String lessonJid, String languageCode) {
        return getStatementsDirPath(userJid, lessonJid).resolve(languageCode);
    }

    private Path getStatementTitleFilePath(String userJid, String lessonJid, String languageCode) {
        return getStatementDirPath(userJid, lessonJid, languageCode).resolve("title.txt");
    }

    private Path getStatementTextFilePath(String userJid, String lessonJid, String languageCode) {
        return getStatementDirPath(userJid, lessonJid, languageCode).resolve("text.html");
    }

    private Path getStatementDefaultLanguageFilePath(String userJid, String lessonJid) {
        return getStatementsDirPath(userJid, lessonJid).resolve("defaultLanguage.txt");
    }

    private Path getStatementAvailableLanguagesFilePath(String userJid, String lessonJid) {
        return getStatementsDirPath(userJid, lessonJid).resolve("availableLanguages.txt");
    }

    private Path getStatementMediaDirPath(String userJid, String lessonJid) {
        return getStatementsDirPath(userJid, lessonJid).resolve("resources");
    }

    private static Path getOriginDirPath(String lessonJid) {
        return Paths.get(SandalphonProperties.getInstance().getBaseLessonsDirKey(), lessonJid);
    }

    private static Path getClonesDirPath(String lessonJid) {
        return Paths.get(SandalphonProperties.getInstance().getBaseLessonClonesDirKey(), lessonJid);
    }

    private static Path getCloneDirPath(String userJid, String lessonJid) {
        return getClonesDirPath(lessonJid).resolve(userJid);
    }

    private static Path getRootDirPath(FileSystem fs, String userJid, String lessonJid) {
        Path origin = getOriginDirPath(lessonJid);
        if (userJid == null) {
            return origin;
        }

        Path root = getCloneDirPath(userJid, lessonJid);
        if (!fs.directoryExists(root)) {
            return origin;
        } else {
            return root;
        }
    }

    private static  Lesson createLessonFromModel(LessonModel lessonModel) {
        return new Lesson.Builder()
                .id(lessonModel.id)
                .jid(lessonModel.jid)
                .slug(lessonModel.slug)
                .authorJid(lessonModel.createdBy)
                .additionalNote(lessonModel.additionalNote)
                .lastUpdateTime(lessonModel.createdAt)
                .build();
    }

    private LessonPartner createLessonPartnerFromModel(LessonPartnerModel lessonPartnerModel) {
        try {
            return new LessonPartner.Builder()
                    .id(lessonPartnerModel.id)
                    .lessonJid(lessonPartnerModel.lessonJid)
                    .userJid(lessonPartnerModel.userJid)
                    .config(mapper.readValue(lessonPartnerModel.config, LessonPartnerConfig.class))
                    .build();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
