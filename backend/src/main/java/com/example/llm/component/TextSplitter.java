package com.example.llm.component;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class TextSplitter {

    /**
     * 改进的分块算法：按段落或句子边界切分，尽量不破坏完整句子
     */
    public List<String> splitText(String text, int chunkSize, int chunkOverlap) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) {
            return chunks;
        }

        // 统一换行符并移除多余空白
        text = text.replaceAll("\r\n", "\n").replaceAll("[ \t]+", " ").trim();

        // 定义切分符（优先级：双换行、单换行、句号/感叹号/问号）
        String[] separators = {"\n\n", "\n", "。", "！", "？", ".", "!", "?"};
        
        List<String> splits = recursiveSplit(text, separators, 0, chunkSize);
        
        // 组装并处理 overlap
        StringBuilder currentChunk = new StringBuilder();
        int currentLength = 0;
        
        for (int i = 0; i < splits.size(); i++) {
            String split = splits.get(i);
            if (currentLength + split.length() > chunkSize && currentLength > 0) {
                chunks.add(currentChunk.toString().trim());
                
                // 处理 overlap：保留当前 chunk 尾部的一些部分
                String prevChunkStr = currentChunk.toString();
                int overlapStart = Math.max(0, prevChunkStr.length() - chunkOverlap);
                // 尝试找一个好的起始位置（标点符号后）
                int bestStart = prevChunkStr.indexOf("。", overlapStart);
                if (bestStart == -1 || bestStart > prevChunkStr.length() - 10) {
                    bestStart = overlapStart;
                } else {
                    bestStart += 1;
                }
                
                currentChunk = new StringBuilder(prevChunkStr.substring(bestStart));
                currentLength = currentChunk.length();
            }
            currentChunk.append(split);
            currentLength += split.length();
        }
        
        if (currentLength > 0) {
            chunks.add(currentChunk.toString().trim());
        }

        return chunks;
    }

    private List<String> recursiveSplit(String text, String[] separators, int separatorIndex, int chunkSize) {
        List<String> result = new ArrayList<>();
        if (text.length() <= chunkSize || separatorIndex >= separators.length) {
            // 已经是最小块或者没有更多分隔符可用，按字符硬切分
            if (text.length() > chunkSize) {
                for (int i = 0; i < text.length(); i += chunkSize) {
                    result.add(text.substring(i, Math.min(i + chunkSize, text.length())));
                }
            } else {
                result.add(text);
            }
            return result;
        }

        String separator = separators[separatorIndex];
        // 如果文本中包含该分隔符
        if (text.contains(separator)) {
            // 对于正则表达式特殊字符需要转义
            String regexSeparator = separator.replace(".", "\\.").replace("?", "\\?");
            String[] parts = text.split("(?<=" + regexSeparator + ")"); // 保留分隔符在结尾
            
            for (String part : parts) {
                if (part.length() > chunkSize) {
                    result.addAll(recursiveSplit(part, separators, separatorIndex + 1, chunkSize));
                } else {
                    result.add(part);
                }
            }
        } else {
            // 如果不包含该分隔符，尝试下一个分隔符
            result.addAll(recursiveSplit(text, separators, separatorIndex + 1, chunkSize));
        }
        return result;
    }
}
