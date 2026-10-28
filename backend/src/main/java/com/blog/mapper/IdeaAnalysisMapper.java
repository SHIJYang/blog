package com.blog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.blog.entity.IdeaAnalysis;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;

public interface IdeaAnalysisMapper extends BaseMapper<IdeaAnalysis> {

    /** jsonb 列必须显式转型，否则 pgjdbc 会把 varchar 当 jsonb 写入而报类型不匹配 */
    @Insert("""
            INSERT INTO idea_analysis (idea_id, user_id, kind, model, prompt_scene, result_json, degraded, create_time)
            VALUES (#{a.ideaId}, #{a.userId}, #{a.kind}, #{a.model}, #{a.promptScene},
                    CAST(#{a.resultJson} AS jsonb), #{a.degraded}, now())
            """)
    @Options(useGeneratedKeys = true, keyProperty = "a.id", keyColumn = "id")
    int insertJson(@Param("a") IdeaAnalysis analysis);
}
