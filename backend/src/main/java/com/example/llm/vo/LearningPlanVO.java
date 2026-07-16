package com.example.llm.vo;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class LearningPlanVO {
    private Long id;
    private Long libraryId;
    private String libraryName;
    private String title;
    private String goal;
    private List<ProfileItem> profile;
    private List<StageVO> stages;
    private List<ResourceVO> resources;
    private List<AgentVO> agents;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @Data
    public static class ProfileItem {
        private String label;
        private String value;
    }

    @Data
    public static class StageVO {
        private Long id;
        private Integer stageOrder;
        private String title;
        private String duration;
        private String goal;
        private List<String> resources;
        private String status;
    }

    @Data
    public static class ResourceVO {
        private Long id;
        private String group;
        private String title;
        private String desc;
        private String action;
    }

    @Data
    public static class AgentVO {
        private String name;
        private String desc;
        private String status;
    }
}
