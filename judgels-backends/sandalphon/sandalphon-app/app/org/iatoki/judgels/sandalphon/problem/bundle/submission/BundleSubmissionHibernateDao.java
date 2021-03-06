package org.iatoki.judgels.sandalphon.problem.bundle.submission;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import judgels.persistence.hibernate.HibernateDaoData;

@Singleton
@Named("bundleSubmissionDao")
public final class BundleSubmissionHibernateDao extends AbstractBundleSubmissionHibernateDao<BundleSubmissionModel> implements BundleSubmissionDao {

    @Inject
    public BundleSubmissionHibernateDao(HibernateDaoData data) {
        super(data);
    }

    @Override
    public BundleSubmissionModel createSubmissionModel() {
        return new BundleSubmissionModel();
    }
}
