package org.support.project.knowledge.filter;

import java.io.IOException;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.support.project.common.log.Log;
import org.support.project.common.log.LogFactory;
import org.support.project.common.util.DateUtils;
import org.support.project.common.util.StringUtils;
import org.support.project.di.Container;
import org.support.project.knowledge.config.AppConfig;
import org.support.project.knowledge.logic.KnowledgeAuthenticationLogic;
import org.support.project.web.bean.LoginedUser;
import org.support.project.web.bean.Msg;
import org.support.project.web.boundary.JsonBoundary;
import org.support.project.web.common.HttpStatus;
import org.support.project.web.common.HttpStatusMsg;
import org.support.project.web.common.InvokeTarget;
import org.support.project.web.dao.TokensDao;
import org.support.project.web.dao.UsersDao;
import org.support.project.web.entity.TokensEntity;
import org.support.project.web.entity.UsersEntity;
import org.support.project.web.exception.CallControlException;
import org.support.project.web.filter.ControlFilter;
import org.support.project.web.logic.AuthenticationLogic;
import org.support.project.web.logic.CallControlLogic;
import org.support.project.web.logic.invoke.CallControlExLogicImpl;
import org.support.project.web.wrapper.HttpServletRequestWrapper;

public class ApiFilter extends ControlFilter {
    /** ログ */
    private static Log LOG = LogFactory.getLog(ApiFilter.class);
    
    private boolean enableAuthParameter = true; // TODO 後でfalseへ
    
    private String targetRegex = "(^/api)(.)*|(^/_api)(.)*";
    private Pattern pattern;

    
    @Override
    public void init(final FilterConfig filterconfig) throws ServletException {
        String targetRegex = filterconfig.getInitParameter("target-regex");
        if (StringUtils.isNotEmpty(targetRegex)) {
            this.targetRegex = targetRegex;
        }
        this.pattern = Pattern.compile(this.targetRegex);

        String authParam = filterconfig.getInitParameter("enable-auth-parameter");
        if (StringUtils.isNotEmpty(authParam)) {
            if (authParam.toLowerCase().equals("true")) {
                enableAuthParameter = true;
            }
        }
    }
    @Override
    public void destroy() {
        this.targetRegex = null;
    }
    
    private void sendError(HttpServletRequest req, HttpServletResponse res, int httpstatus) throws ServletException {
        String msg = "INTERNAL_SERVER_ERROR";
        if (StringUtils.isNotEmpty(HttpStatusMsg.getMsg(httpstatus))) {
            msg = HttpStatusMsg.getMsg(httpstatus);
        }
        sendError(req, res, httpstatus, msg);
    }
    private void sendError(HttpServletRequest req, HttpServletResponse res, int httpstatus, String msg) throws ServletException {
        try {
            res.setStatus(httpstatus);
            Msg nsg = new Msg(msg);
            JsonBoundary boundary = new JsonBoundary(nsg);
            boundary.setRequest(req);
            boundary.setResponse(res);
            boundary.navigate();
        } catch (Exception e) {
            throw new ServletException(e);
        }
    }

    @Override
    public void doFilter(ServletRequest servletrequest, ServletResponse servletresponse, FilterChain filterchain)
            throws IOException, ServletException {
        HttpServletRequest req_origin = (HttpServletRequest) servletrequest;
        AuthenticationLogic<LoginedUser> authenticationLogic = (AuthenticationLogic<LoginedUser>) Container.getComp(KnowledgeAuthenticationLogic.class);
        HttpServletRequestWrapper req = new HttpServletRequestWrapper((HttpServletRequest) req_origin, authenticationLogic);
        HttpServletResponse res = (HttpServletResponse) servletresponse;

        // 対象外のパスであれば、処理対象から除外（/api から始まるもののみ）
        StringBuilder pathBuilder = new StringBuilder();
        pathBuilder.append(req.getServletPath());
        if (req.getPathInfo() != null && req.getPathInfo().length() > 0) {
            pathBuilder.append(req.getPathInfo());
        }
        String path = pathBuilder.toString();
        Matcher matcher = pattern.matcher(path);
        if (!matcher.find()) {
            // 対象外
            filterchain.doFilter(req, res);
            return;
        }
        // メンテナンス中であれば、APIのパスは 503 を返す
        if (AppConfig.get().isMaintenanceMode()) {
            sendError(req, res, HttpStatus.SC_503_SERVICE_UNAVAILABLE);
            return;
        }
        
        // 認証(Tokenが指定されていれば、ログイン状態にする、指定していなければ何もしない）
        // Httpヘッダー「PRIVATE-TOKEN」か、リクエストパラメータ「private_token」の値で認証することを検討（GitLab準拠）
        // → クエリパラメータ指定だとアクセスログにtoken情報が表示されてしまい良くない気がするので、デフォルトはHttpヘッダー指定のみにする（設定で有効にもできる）
        // 
        // なお、OAuthに関連した仕様で RFC6750 があり、それだと、「Authorization: Bearer ヘッダ」を使う事が推奨なのだけど、
        // OAuthを提供しているわけでは無いので、GitLab方式を採用している（外部からのWebAPI／内部API共に）
        String token = req.getHeader("PRIVATE-TOKEN");
        if (enableAuthParameter) {
            if (StringUtils.isEmpty(token)) {
                token = req.getParameter("private_token");
            }
        }
        boolean setSession = false;
        TokensEntity tokensEntity = TokensDao.get().selectOnKey(token);
        if (tokensEntity != null) {
            Date now = DateUtils.now();
            if (now.getTime() < tokensEntity.getExpires().getTime()) {
                // APIを使う場合、Tokensテーブルの「更新日付」現在の日付でアップデートする（最終利用日時）
                TokensDao.get().update(tokensEntity);
                // セッション生成
                UsersEntity user = UsersDao.get().selectOnKey(tokensEntity.getUserId());
                if (user != null) {
                    // 毎回セッションを生成して登録する（毎回なので少し重いかも）
                    authenticationLogic.setSession(user.getUserKey(), req, res);
                    setSession = true;
                }
            }
        }
        // APIの実際の処理を呼び出し
        try {
            CallControlLogic callControlLogic = Container.getComp(CallControlExLogicImpl.class);
            InvokeTarget invokeTarget = callControlLogic.searchInvokeTarget(req, res);
            if (invokeTarget == null) {
                sendError(req, res, HttpStatus.SC_404_NOT_FOUND);
                return;
            }
            invoke(invokeTarget, req, res);
            // リクエスト毎にセッションはクリアする
            if (setSession) {
                authenticationLogic.clearSession(req);
            }
        } catch (CallControlException e) {
            LOG.warn("error", e);
            sendError(req, res, e.getHttpStatus());
        } catch (Exception e) {
            LOG.error("error", e);
            sendError(req, res, HttpStatus.SC_500_INTERNAL_SERVER_ERROR);
        }
    }


}
