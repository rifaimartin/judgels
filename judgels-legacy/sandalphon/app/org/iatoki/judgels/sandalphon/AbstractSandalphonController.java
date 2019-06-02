package org.iatoki.judgels.sandalphon;

import org.iatoki.judgels.jophiel.JophielClientControllerUtils;
import org.iatoki.judgels.jophiel.logincheck.html.isLoggedInLayout;
import org.iatoki.judgels.jophiel.logincheck.html.isLoggedOutLayout;
import org.iatoki.judgels.play.AbstractJudgelsController;
import org.iatoki.judgels.play.IdentityUtils;
import org.iatoki.judgels.play.template.HtmlTemplate;
import org.iatoki.judgels.play.views.html.layouts.profileView;
import play.i18n.Messages;
import play.mvc.Http;
import play.mvc.Result;

public abstract class AbstractSandalphonController extends AbstractJudgelsController {
    @Override
    protected Result renderTemplate(HtmlTemplate template) {
        template.addSidebarMenu(Messages.get("problem.problems"), org.iatoki.judgels.sandalphon.problem.base.routes.ProblemController.index());
        template.addSidebarMenu(Messages.get("lesson.lessons"), org.iatoki.judgels.sandalphon.lesson.routes.LessonController.index());
        if (isAdmin()) {
            template.addSidebarMenu(Messages.get("user.users"), org.iatoki.judgels.sandalphon.user.routes.UserController.index());
        }

        template.addUpperSidebarWidget(profileView.render(
                IdentityUtils.getUsername(),
                IdentityUtils.getUserRealName(),
                org.iatoki.judgels.jophiel.routes.JophielClientController.profile().absoluteURL(Http.Context.current().request(), Http.Context.current().request().secure()),
                org.iatoki.judgels.jophiel.routes.JophielClientController.logout(routes.ApplicationController.index().absoluteURL(Http.Context.current().request(), Http.Context.current().request().secure())).absoluteURL(Http.Context.current().request(), Http.Context.current().request().secure())
        ));
        if (IdentityUtils.getUserJid() == null) {
            template.addAdditionalScript(isLoggedInLayout.render(JophielClientControllerUtils.getInstance().getUserIsLoggedInAPIEndpoint(), routes.ApplicationController.auth(getCurrentUrl(Http.Context.current().request())).absoluteURL(Http.Context.current().request(), Http.Context.current().request().secure()), "lib/jophielcommons/javascripts/isLoggedIn.js", null));
        } else {
            template.addAdditionalScript(isLoggedOutLayout.render(JophielClientControllerUtils.getInstance().getUserIsLoggedInAPIEndpoint(), routes.ApplicationController.logout(getCurrentUrl(Http.Context.current().request())).absoluteURL(Http.Context.current().request(), Http.Context.current().request().secure()), "lib/jophielcommons/javascripts/isLoggedOut.js", SandalphonUtils.getRealUserJid(), null));
        }

        return super.renderTemplate(template);
    }

    protected boolean isAdmin() {
        return SandalphonUtils.hasRole("admin");
    }
}
