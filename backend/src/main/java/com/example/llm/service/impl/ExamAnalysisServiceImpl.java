package com.example.llm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.llm.component.DocumentParser;
import com.example.llm.dto.ExamAnalysisUpdateReq;
import com.example.llm.entity.ExamAnalysis;
import com.example.llm.mapper.ExamAnalysisMapper;
import com.example.llm.service.ExamAnalysisService;
import com.alibaba.dashscope.aigc.generation.Generation;
import com.alibaba.dashscope.aigc.generation.GenerationParam;
import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.common.Message;
import com.alibaba.dashscope.common.Role;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class ExamAnalysisServiceImpl extends ServiceImpl<ExamAnalysisMapper, ExamAnalysis> implements ExamAnalysisService {

    @Value("${dashscope.api-key}")
    private String apiKey;

    @Value("${upload.path}")
    private String uploadPath;

    @Autowired
    private DocumentParser documentParser;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public List<ExamAnalysis> listExamAnalyses(Long userId) {
        LambdaQueryWrapper<ExamAnalysis> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ExamAnalysis::getUserId, userId);
        wrapper.eq(ExamAnalysis::getStatus, 0);
        wrapper.orderByDesc(ExamAnalysis::getCreateTime);
        return this.list(wrapper);
    }

    @Override
    public ExamAnalysis getExamAnalysisDetail(Long id, Long userId) {
        ExamAnalysis analysis = this.getById(id);
        if (analysis == null || !analysis.getUserId().equals(userId) || analysis.getStatus() != 0) {
            throw new RuntimeException("分析记录不存在或无权访问");
        }
        return analysis;
    }

    @Override
    public Long createExamAnalysis(String title, String examType, String fileNames, List<MultipartFile> files, Long userId) {
        StringBuilder contentBuilder = new StringBuilder();
        File uploadDir = new File(new File(uploadPath).getAbsoluteFile(), "exam-analysis");
        if (!uploadDir.exists()) {
            uploadDir.mkdirs();
        }

        if (files != null) {
            for (MultipartFile file : files) {
                if (file.isEmpty()) continue;
                try {
                    String savedFileName = UUID.randomUUID().toString().replace("-", "") + "_" + file.getOriginalFilename();
                    File destFile = new File(uploadDir, savedFileName).getAbsoluteFile();
                    file.transferTo(destFile);

                    String text = documentParser.parse(destFile);
                    contentBuilder.append("=== 文件: ").append(file.getOriginalFilename()).append(" ===\n");
                    contentBuilder.append(text).append("\n\n");
                } catch (Exception e) {
                    log.error("Failed to parse file: {}", file.getOriginalFilename(), e);
                }
            }
        }

        ExamAnalysis analysis = new ExamAnalysis();
        analysis.setUserId(userId);
        analysis.setTitle(title);
        analysis.setExamType(examType);
        analysis.setFileNames(fileNames);
        analysis.setContent(contentBuilder.toString());
        analysis.setStatus(0);
        this.save(analysis);
        return analysis.getId();
    }

    @Override
    public void updateExamAnalysis(ExamAnalysisUpdateReq req, Long userId) {
        ExamAnalysis analysis = this.getById(req.getId());
        if (analysis == null || !analysis.getUserId().equals(userId)) {
            throw new RuntimeException("分析记录不存在或无权访问");
        }
        if (req.getTitle() != null) analysis.setTitle(req.getTitle());
        if (req.getKeyPoints() != null) analysis.setKeyPoints(req.getKeyPoints());
        if (req.getQuestionDistribution() != null) analysis.setQuestionDistribution(req.getQuestionDistribution());
        if (req.getSuggestions() != null) analysis.setSuggestions(req.getSuggestions());
        if (req.getMindMapId() != null) analysis.setMindMapId(req.getMindMapId());
        this.updateById(analysis);
    }

    @Override
    public void deleteExamAnalysis(Long id, Long userId) {
        ExamAnalysis analysis = this.getById(id);
        if (analysis == null || !analysis.getUserId().equals(userId)) {
            throw new RuntimeException("分析记录不存在或无权访问");
        }
        analysis.setStatus(1);
        this.updateById(analysis);
    }

    @Override
    public ExamAnalysis analyzeExam(Long id, Long userId) {
        ExamAnalysis analysis = this.getById(id);
        if (analysis == null || !analysis.getUserId().equals(userId)) {
            throw new RuntimeException("分析记录不存在或无权访问");
        }

        try {
            String prompt = buildAnalysisPrompt(analysis);
            Generation gen = new Generation();
            GenerationParam param = GenerationParam.builder()
                    .apiKey(apiKey)
                    .model("qwen-plus")
                    .messages(java.util.Collections.singletonList(
                            Message.builder().role(Role.USER.getValue()).content(prompt).build()))
                    .resultFormat(GenerationParam.ResultFormat.MESSAGE)
                    .build();
            GenerationResult result = gen.call(param);
            String responseText = result.getOutput().getChoices().get(0).getMessage().getContent().trim();

            parseAnalysisResponse(analysis, responseText);
            this.updateById(analysis);
        } catch (Exception e) {
            log.error("Failed to analyze exam", e);
            setDefaultAnalysis(analysis);
            this.updateById(analysis);
        }

        return analysis;
    }

    @Override
    public ExamAnalysis generateSuggestions(Long id, Long userId) {
        ExamAnalysis analysis = this.getById(id);
        if (analysis == null || !analysis.getUserId().equals(userId)) {
            throw new RuntimeException("分析记录不存在或无权访问");
        }

        try {
            String prompt = buildSuggestionPrompt(analysis);
            Generation gen = new Generation();
            GenerationParam param = GenerationParam.builder()
                    .apiKey(apiKey)
                    .model("qwen-plus")
                    .messages(java.util.Collections.singletonList(
                            Message.builder().role(Role.USER.getValue()).content(prompt).build()))
                    .resultFormat(GenerationParam.ResultFormat.MESSAGE)
                    .build();
            GenerationResult result = gen.call(param);
            String suggestions = result.getOutput().getChoices().get(0).getMessage().getContent().trim();
            analysis.setSuggestions(suggestions);
            this.updateById(analysis);
        } catch (Exception e) {
            log.error("Failed to generate suggestions", e);
            analysis.setSuggestions("暂无法生成复习建议，请稍后重试。");
            this.updateById(analysis);
        }

        return analysis;
    }

    private String buildAnalysisPrompt(ExamAnalysis analysis) {
        String content = analysis.getContent() != null && !analysis.getContent().isEmpty()
                ? analysis.getContent()
                : "未提供试卷内容，仅根据考试类型进行通用分析。";

        return "你是一个专业的考试分析专家。请根据以下考试信息，分析高频考点和题型分布。\n" +
                "考试类型：" + analysis.getExamType() + "\n" +
                "试卷内容：\n" + content + "\n\n" +
                "请以JSON格式返回分析结果，格式如下：\n" +
                "{\n" +
                "  \"keyPoints\": [\n" +
                "    {\"name\": \"考点名称\", \"count\": 出现次数},\n" +
                "    {\"name\": \"考点名称\", \"count\": 出现次数}\n" +
                "  ],\n" +
                "  \"questionDistribution\": [\n" +
                "    {\"type\": \"题型名称\", \"percentage\": 占比百分比},\n" +
                "    {\"type\": \"题型名称\", \"percentage\": 占比百分比}\n" +
                "  ]\n" +
                "}\n\n" +
                "请只返回JSON，不要有任何其他文字。";
    }

    private String buildSuggestionPrompt(ExamAnalysis analysis) {
        String keyPointsStr = analysis.getKeyPoints() != null ? analysis.getKeyPoints() : "暂无";
        String distStr = analysis.getQuestionDistribution() != null ? analysis.getQuestionDistribution() : "暂无";
        return "你是一个专业的考试复习顾问。请根据以下考试分析结果，给出具体的复习建议。\n" +
                "考试类型：" + analysis.getExamType() + "\n" +
                "高频考点：" + keyPointsStr + "\n" +
                "题型分布：" + distStr + "\n\n" +
                "请给出3-5条具体的、有针对性的复习建议，每条建议要包含具体的行动方案。";
    }

    private void parseAnalysisResponse(ExamAnalysis analysis, String responseText) {
        try {
            String jsonStr = responseText;
            if (jsonStr.contains("```json")) {
                jsonStr = jsonStr.substring(jsonStr.indexOf("```json") + 7);
                jsonStr = jsonStr.substring(0, jsonStr.indexOf("```"));
            } else if (jsonStr.contains("```")) {
                jsonStr = jsonStr.substring(jsonStr.indexOf("```") + 3);
                jsonStr = jsonStr.substring(0, jsonStr.indexOf("```"));
            }
            jsonStr = jsonStr.trim();

            com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(jsonStr);

            if (root.has("keyPoints")) {
                analysis.setKeyPoints(objectMapper.writeValueAsString(root.get("keyPoints")));
            }
            if (root.has("questionDistribution")) {
                analysis.setQuestionDistribution(objectMapper.writeValueAsString(root.get("questionDistribution")));
            }
        } catch (Exception e) {
            log.warn("Failed to parse analysis response as JSON, storing raw text", e);
            analysis.setKeyPoints("[{\"name\":\"综合分析\",\"count\":1}]");
            analysis.setQuestionDistribution("[{\"type\":\"综合题型\",\"percentage\":100}]");
        }
    }

    private void setDefaultAnalysis(ExamAnalysis analysis) {
        analysis.setKeyPoints("[{\"name\":\"综合考点\",\"count\":1}]");
        analysis.setQuestionDistribution("[{\"type\":\"综合题型\",\"percentage\":100}]");
    }
}
