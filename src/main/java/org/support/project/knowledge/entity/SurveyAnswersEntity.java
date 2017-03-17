package org.support.project.knowledge.entity;

import java.util.ArrayList;
import java.util.List;

import org.support.project.di.Container;
import org.support.project.di.DI;
import org.support.project.di.Instance;
import org.support.project.knowledge.entity.gen.GenSurveyAnswersEntity;


/**
 * アンケートの回答
 */
@DI(instance = Instance.Prototype)
public class SurveyAnswersEntity extends GenSurveyAnswersEntity {

    private List<SurveyItemAnswersEntity> items = new ArrayList<SurveyItemAnswersEntity>();
    
    /** SerialVersion */
    private static final long serialVersionUID = 1L;

    /**
     * Get instance from DI container.
     * @return instance
     */
    public static SurveyAnswersEntity get() {
        return Container.getComp(SurveyAnswersEntity.class);
    }

    /**
     * Constructor.
     */
    public SurveyAnswersEntity() {
        super();
    }

    /**
     * Constructor
     * @param answerId 回答ID
     * @param knowledgeId ナレッジID
     */

    public SurveyAnswersEntity(Integer answerId, Long knowledgeId) {
        super( answerId,  knowledgeId);
    }

    /**
     * @return the items
     */
    public List<SurveyItemAnswersEntity> getItems() {
        return items;
    }

    /**
     * @param items the items to set
     */
    public void setItems(List<SurveyItemAnswersEntity> items) {
        this.items = items;
    }

}
