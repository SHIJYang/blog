package com.blog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.blog.entity.AiPrompt;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface IdeaTransitionRuleMapper {

    /** 某状态允许迁移到的目标状态（表驱动，插入一行即放开一条边） */
    @Select("SELECT to_state FROM idea_transition_rule WHERE from_state = #{fromState}")
    List<String> selectTargets(@Param("fromState") String fromState);

    @Select("""
            SELECT COUNT(*) FROM idea_transition_rule
            WHERE from_state = #{fromState} AND to_state = #{toState}
            """)
    int countRule(@Param("fromState") String fromState, @Param("toState") String toState);
}
