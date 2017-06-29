package org.support.project.knowledge.logic;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.support.project.common.bean.ValidateError;
import org.support.project.common.log.Log;
import org.support.project.common.log.LogFactory;
import org.support.project.common.util.PropertyUtil;
import org.support.project.common.util.StringJoinBuilder;
import org.support.project.di.Container;
import org.support.project.di.DI;
import org.support.project.di.Instance;
import org.support.project.knowledge.dao.TemplateMastersDao;
import org.support.project.knowledge.entity.KnowledgesEntity;
import org.support.project.knowledge.entity.TemplateItemsEntity;
import org.support.project.knowledge.entity.TemplateMastersEntity;
import org.support.project.knowledge.vo.KnowledgeData;
import org.support.project.knowledge.vo.api.KnowledgeDetail;
import org.support.project.knowledge.vo.api.Target;
import org.support.project.web.bean.LabelValue;
import org.support.project.web.bean.LoginedUser;
import org.support.project.web.bean.MessageResult;
import org.support.project.web.bean.NameId;
import org.support.project.web.common.HttpStatus;
import org.support.project.web.config.MessageStatus;
import org.support.project.web.exception.InvalidParamException;

@DI(instance = Instance.Singleton)
public class KnowledgeDataEditLogic {
    /** LOG */
    private static final Log LOG = LogFactory.getLog(KnowledgeDataEditLogic.class);
    /** Get instance */
    public static KnowledgeDataEditLogic get() {
        return Container.getComp(KnowledgeDataEditLogic.class);
    }
    
    /**
     * WebAPIで受信したデータを画面で登録した形式に合わせてデータ変換
     * @param data
     * @return
     * @throws InvalidParamException 
     */
    private KnowledgeData conv(KnowledgeDetail data) throws InvalidParamException {
        KnowledgeData knowledge = new KnowledgeData();
        
        // KnowledgesEntity knowledge
        KnowledgesEntity entity = new KnowledgesEntity();
        PropertyUtil.copyPropertyValue(data, entity);
        
        List<ValidateError> errors = entity.validate();
        if (errors != null && !errors.isEmpty()) {
            StringJoinBuilder<String> builder = new StringJoinBuilder<>();
            for (ValidateError validateError : errors) {
                builder.append(validateError.getMsg(Locale.ENGLISH));
            }
            throw new InvalidParamException(new MessageResult(
                    MessageStatus.Warning, HttpStatus.SC_400_BAD_REQUEST, builder.join(","), ""));
        }
        entity.setKnowledgeId(null);
        knowledge.setKnowledge(entity);
        
        // List<TagsEntity> tags
        StringJoinBuilder<String> tags = new StringJoinBuilder<>(data.getTags());
        knowledge.setTagsStr(tags.join(","));
        
        // List<LabelValue> viewers, editors
        knowledge.setViewers(convTargets(data.getViewers()));
        knowledge.setEditors(convTargets(data.getEditors()));
        
        // List<Long> fileNos (現在未対応）
        knowledge.setFileNos(new ArrayList<>());
        
        // TemplateMastersEntity template
        TemplateMastersEntity template = TemplateLogic.get().selectOnName(data.getTemplate());
        if (template == null) {
            throw new InvalidParamException(new MessageResult(
                    MessageStatus.Warning, HttpStatus.SC_400_BAD_REQUEST, "bad template name", ""));
        }
        List<TemplateItemsEntity> items = template.getItems();
        for (TemplateItemsEntity item : items) {
            List<LabelValue> vals = data.getTemplateItems();
            for (LabelValue labelValue : vals) {
                if (item.getItemName().equals(labelValue.getLabel())) {
                    item.setItemValue(labelValue.getValue());
                }
            }
        }
        knowledge.setTemplate(template);
        entity.setTypeId(template.getTypeId());
        
        // Long draftId
        knowledge.setDraftId(null);
        
        // boolean updateContent
        knowledge.setUpdateContent(true);
        
        return knowledge;
    }
    
    private String convTargets(Target viewers) {
        StringJoinBuilder<String> builder = new StringJoinBuilder<>();
        List<NameId> groups = viewers.getGroups();
        if (groups != null) {
            for (NameId nameId : groups) {
                builder.append(TargetLogic.ID_PREFIX_GROUP.concat(nameId.getId()));
            }
        }
        List<NameId> users = viewers.getUsers();
        if (users != null) {
            for (NameId nameId : users) {
                builder.append(TargetLogic.ID_PREFIX_USER.concat(nameId.getId()));
            }
        }
        return builder.join(",");
    }

    /**
     * WebAPIからの Knowledge登録
     * @param data
     * @param loginedUser
     * @return
     * @throws Exception 
     */
    public long insert(KnowledgeDetail data, LoginedUser loginedUser) throws Exception {
        LOG.trace("save");
        // 画面での登録と形をあわせる
        KnowledgeData knowledge = conv(data);
        KnowledgesEntity insertedEntity = KnowledgeLogic.get().insert(knowledge, loginedUser);
        return insertedEntity.getKnowledgeId();
    }







}
